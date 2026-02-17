nxf_mem_watch_cgroup() {
    local pid=$1
    local trace_file=$2

    local count=0
    declare -a mem_peak=(0 0 0 0 0 0 0 0)
    declare -a cpu_peak=(0 0 0 0 0 0 0 0)
    declare -a cpu_stat
    local timeout
    local DONE
    local STOP=''

    local cgroup_path=""
    local cgroup_version=""

    if [ -f "/sys/fs/cgroup/cgroup.controllers" ]; then
        cgroup_version="v2"
        local cgroup_rel_path=$(cat /proc/$pid/cgroup 2>/dev/null | grep '^0::' | cut -d: -f3)
        if [ -n "$cgroup_rel_path" ]; then
            cgroup_path="/sys/fs/cgroup${cgroup_rel_path}"
        fi

    elif [ -d "/sys/fs/cgroup/memory" ]; then
        cgroup_version="v1"
        local cgroup_rel_path=$(cat /proc/$pid/cgroup 2>/dev/null | grep ':memory:' | cut -d: -f3)
        if [ -n "$cgroup_rel_path" ]; then
            cgroup_path="/sys/fs/cgroup/memory${cgroup_rel_path}"
        fi
    fi

    if [ -z "$cgroup_path" ] || [ ! -d "$cgroup_path" ]; then
        if [ "$cgroup_version" = "v2" ] && [ -f "/sys/fs/cgroup/memory.current" ]; then
            cgroup_path="/sys/fs/cgroup"
        elif [ "$cgroup_version" = "v1" ] && [ -f "/sys/fs/cgroup/memory/memory.usage_in_bytes" ]; then
            cgroup_path="/sys/fs/cgroup/memory"
        else
            return 1
        fi
    fi

    local mem_tot=$(awk '/MemTotal/ {print $2}' /proc/meminfo)

    while true; do
        declare -a mem_stat=(0 0 0 0 0 0 0 0)

        if [ "$cgroup_version" = "v2" ]; then
            if [ -f "$cgroup_path/memory.current" ]; then
                local mem_current=$(cat "$cgroup_path/memory.current" 2>/dev/null || echo 0)
                local mem_max=$(cat "$cgroup_path/memory.peak" 2>/dev/null || echo $mem_current)

                local mem_anon=0
                if [ -f "$cgroup_path/memory.stat" ]; then
                    mem_anon=$(awk '/^anon / {print $2}' "$cgroup_path/memory.stat" 2>/dev/null || echo 0)
                fi

                mem_stat[2]=$((mem_current / 1024))  
                mem_stat[3]=$((mem_anon / 1024))     
                mem_stat[4]=$((mem_max / 1024))

                local pmem=$(awk -v rss=${mem_stat[3]} -v mem_tot=$mem_tot 'BEGIN {printf "%.0f", rss/mem_tot*100*10}')
                mem_stat[1]=$pmem
            fi

        elif [ "$cgroup_version" = "v1" ]; then
            if [ -f "$cgroup_path/memory.usage_in_bytes" ]; then
                local mem_usage=$(cat "$cgroup_path/memory.usage_in_bytes" 2>/dev/null || echo 0)
                local mem_max=$(cat "$cgroup_path/memory.max_usage_in_bytes" 2>/dev/null || echo 0)

                local mem_rss=0
                local mem_rss_huge=0
                if [ -f "$cgroup_path/memory.stat" ]; then
                    mem_rss=$(awk '/^rss / {print $2}' "$cgroup_path/memory.stat" 2>/dev/null || echo 0)
                    mem_rss_huge=$(awk '/^rss_huge / {print $2}' "$cgroup_path/memory.stat" 2>/dev/null || echo 0)
                fi

                local mem_rss_total=$((mem_rss + mem_rss_huge))

                mem_stat[2]=$((mem_usage / 1024))
                mem_stat[3]=$((mem_rss_total / 1024))
                mem_stat[4]=$((mem_max / 1024))

                local pmem=$(awk -v rss=${mem_stat[3]} -v mem_tot=$mem_tot 'BEGIN {printf "%.0f", rss/mem_tot*100*10}')
                mem_stat[1]=$pmem
            fi
        fi

        local i
        for i in {1..7}; do
            if [ ${mem_stat[i]} -gt ${mem_peak[i]} ]; then
                mem_peak[i]=${mem_stat[i]}
            fi
        done

        if [ -e /proc/$pid ]; then
            nxf_stat $pid
        fi

        read -t 1 -r DONE || true
        [[ $DONE ]] && break

        if [ ! -e /proc/$pid ]; then
            [ ! $STOP ] && STOP=$(nxf_date)
            [ $(($(nxf_date) - STOP)) -gt 10000 ] && break
        fi
        count=$((count + 1))
    done

    mem_peak[5]=${mem_peak[3]}

    local vol_ctxt=${nxf_stat_ret[6]:-0}
    local inv_ctxt=${nxf_stat_ret[7]:-0}

    echo "%mem=${mem_peak[1]}" >>$trace_file
    echo "vmem=${mem_peak[2]}" >>$trace_file
    echo "rss=${mem_peak[3]}" >>$trace_file
    echo "peak_vmem=${mem_peak[4]}" >>$trace_file
    echo "peak_rss=${mem_peak[5]}" >>$trace_file
    echo "vol_ctxt=$vol_ctxt" >>$trace_file
    echo "inv_ctxt=$inv_ctxt" >>$trace_file
}
