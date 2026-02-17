package com.watchmakergenomics.cgroup

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
class CgroupScriptUtils {

    static final String CGROUP_CHECK = '''
    # Try cgroup-based tracking (v1 or v2) if available
    if [ -e /proc/$pid/cgroup ] && nxf_mem_watch_cgroup "$pid" "$trace_file"; then
        return 0
    fi

    # Fall back to default /proc-based tracking below
'''

    static String injectCgroupSupport(String script, String cgroupMemWatchFunction) {
        if (script == null) {
            return null
        }

        def funcPattern = ~/nxf_mem_watch\s*\(\s*\)\s*\{/
        def funcMatcher = (script =~ funcPattern)

        if (!funcMatcher.find()) {
            log.warn "Could not find nxf_mem_watch() function declaration"
            return script
        }

        final index = funcMatcher.start()
        final before = script.substring(0, index)
        final after = script.substring(index)

        String result = before + cgroupMemWatchFunction + '\n\n' + after

        // Use string manipulation instead of replaceFirst to avoid $$ corruption
        // (regex replacement interprets $ as backreference)
        def pattern = ~/nxf_mem_watch\(\) \{[\s\S]*?(while true; do)/
        def matcher = (result =~ pattern)

        if (matcher.find()) {
            def insertPos = matcher.start(1)
            result = result.substring(0, insertPos) + CGROUP_CHECK + result.substring(insertPos)
            log.debug "Successfully inserted cgroup check before 'while true; do'"
            return result
        }

        pattern = ~/nxf_mem_watch\(\) \{[\s\S]*?(while :; do)/
        matcher = (result =~ pattern)

        if (matcher.find()) {
            def insertPos = matcher.start(1)
            result = result.substring(0, insertPos) + CGROUP_CHECK + result.substring(insertPos)
            log.debug "Successfully inserted cgroup check before 'while :; do'"
            return result
        }

        pattern = ~/nxf_mem_watch\(\) \{[\s\S]*?(local STOP=''\n)/
        matcher = (result =~ pattern)

        if (matcher.find()) {
            def insertPos = matcher.end(1)
            result = result.substring(0, insertPos) + CGROUP_CHECK + result.substring(insertPos)
            log.debug "Successfully inserted cgroup check after 'local STOP='''"
            return result
        }

        log.warn "Could not find insertion point in nxf_mem_watch() - all patterns failed"
        return script
    }

    static boolean hasMemWatchFunction(String script) {
        return script?.contains('nxf_mem_watch()')
    }
}
