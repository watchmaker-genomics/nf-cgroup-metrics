package com.watchmakergenomics.cgroup

import spock.lang.Specification
import spock.lang.Unroll

class CgroupScriptUtilsSpec extends Specification {

    static final String MOCK_CGROUP_FUNCTION = '''nxf_mem_watch_cgroup() {
    local pid=$1
    local trace_file=$2
    echo "cgroup tracking active"
}'''

    static final String SCRIPT_WITH_WHILE_TRUE = '''#!/bin/bash
nxf_mem_watch() {
    local pid=$1
    local STOP=''

    while true; do
        echo "tracking"
    done
}
'''

    static final String SCRIPT_WITH_WHILE_COLON = '''nxf_mem_watch() {
    local pid=$1
    while :; do
        sleep 1
    done
}'''

    static final String SCRIPT_WITH_LOCAL_STOP = '''nxf_mem_watch() {
    local pid=$1
    local STOP=''

    for (( ; ; )); do
        echo "tracking"
    done
}
'''

    static final String SCRIPT_WITHOUT_MEM_WATCH = '''#!/bin/bash
some_other_function() {
    while true; do
        echo test
    done
}
'''

    static final String SCRIPT_NO_PATTERN_MATCH = '''nxf_mem_watch() {
    run_some_other_monitoring
}'''

    static final String REALISTIC_NEXTFLOW_SCRIPT = '''#!/bin/bash
set -e
set -u

nxf_kill() {
    declare -a children
    kill_pid $1
}

nxf_mem_watch() {
    local pid=$1
    local trace_file=$2
    local count=0
    declare -a mem_peak=(0 0 0 0 0 0 0 0)
    local STOP=''

    while true; do
        declare -a mem_stat=(0 0 0 0 0 0 0 0)
        [[ -e /proc/$pid ]] && mem_stat=($(< /proc/$pid/statm))
        count=$((count+1))
    done
    echo "vmem=${mem_peak[1]}" >> $trace_file
}

nxf_main() {
    echo "Running task"
}
'''

    def "should inject cgroup function when script contains nxf_mem_watch"() {
        when:
        def result = CgroupScriptUtils.injectCgroupSupport(SCRIPT_WITH_WHILE_TRUE, MOCK_CGROUP_FUNCTION)

        then:
        result.contains('nxf_mem_watch_cgroup()')
    }

    def "should place cgroup function before nxf_mem_watch declaration"() {
        when:
        def result = CgroupScriptUtils.injectCgroupSupport(SCRIPT_WITH_WHILE_TRUE, MOCK_CGROUP_FUNCTION)

        then:
        result.indexOf('nxf_mem_watch_cgroup()') < result.indexOf('nxf_mem_watch() {')
    }

    def "should inject cgroup check comment into monitoring function"() {
        when:
        def result = CgroupScriptUtils.injectCgroupSupport(SCRIPT_WITH_WHILE_TRUE, MOCK_CGROUP_FUNCTION)

        then:
        result.contains('# Try cgroup-based tracking')
    }

    def "should place cgroup check before while-true loop"() {
        when:
        def result = CgroupScriptUtils.injectCgroupSupport(SCRIPT_WITH_WHILE_TRUE, MOCK_CGROUP_FUNCTION)

        then:
        result.indexOf('# Try cgroup-based tracking') < result.indexOf('while true; do')
    }

    def "should preserve double dollar signs in script"() {
        given:
        def scriptWithDoubleDollar = '''#!/bin/bash
nxf_trace_linux() {
    local pid=$$
    while [ -e /proc/$$/fd ]; do sleep 1; done
}

nxf_mem_watch() {
    local pid=$1
    local STOP=''

    while true; do
        echo "tracking"
    done
}
'''

        when:
        def result = CgroupScriptUtils.injectCgroupSupport(scriptWithDoubleDollar, MOCK_CGROUP_FUNCTION)

        then:
        result.contains('local pid=$$')
        result.contains('/proc/$$/fd')
    }

    @Unroll
    def "should detect and inject before loop pattern: #patternName"() {
        when:
        def result = CgroupScriptUtils.injectCgroupSupport(script, MOCK_CGROUP_FUNCTION)

        then:
        result.contains('nxf_mem_watch_cgroup()')
        result.contains('# Try cgroup-based tracking')

        where:
        patternName       | script
        'while true; do'  | SCRIPT_WITH_WHILE_TRUE
        'while :; do'     | SCRIPT_WITH_WHILE_COLON
        'local STOP='     | SCRIPT_WITH_LOCAL_STOP
    }

    def "should return original script when nxf_mem_watch function is absent"() {
        when:
        def result = CgroupScriptUtils.injectCgroupSupport(SCRIPT_WITHOUT_MEM_WATCH, MOCK_CGROUP_FUNCTION)

        then:
        result == SCRIPT_WITHOUT_MEM_WATCH
    }

    def "should return original script when no injection patterns match"() {
        when:
        def result = CgroupScriptUtils.injectCgroupSupport(SCRIPT_NO_PATTERN_MATCH, MOCK_CGROUP_FUNCTION)

        then:
        result == SCRIPT_NO_PATTERN_MATCH
    }

    def "should return null when input is null"() {
        when:
        def result = CgroupScriptUtils.injectCgroupSupport(null, MOCK_CGROUP_FUNCTION)

        then:
        result == null
    }

    def "should return original script when input is empty"() {
        given:
        def emptyScript = ''

        when:
        def result = CgroupScriptUtils.injectCgroupSupport(emptyScript, MOCK_CGROUP_FUNCTION)

        then:
        result == emptyScript
    }

    def "should preserve other functions when injecting cgroup support"() {
        when:
        def result = CgroupScriptUtils.injectCgroupSupport(REALISTIC_NEXTFLOW_SCRIPT, MOCK_CGROUP_FUNCTION)

        then:
        result.contains('nxf_kill()')
    }

    def "should preserve main function when injecting cgroup support"() {
        when:
        def result = CgroupScriptUtils.injectCgroupSupport(REALISTIC_NEXTFLOW_SCRIPT, MOCK_CGROUP_FUNCTION)

        then:
        result.contains('nxf_main()')
    }

    def "should preserve shell options when injecting cgroup support"() {
        when:
        def result = CgroupScriptUtils.injectCgroupSupport(REALISTIC_NEXTFLOW_SCRIPT, MOCK_CGROUP_FUNCTION)

        then:
        result.contains('set -e')
        result.contains('set -u')
    }

    def "should not duplicate cgroup function on repeated injection"() {
        given:
        def firstInjection = CgroupScriptUtils.injectCgroupSupport(SCRIPT_WITH_WHILE_TRUE, MOCK_CGROUP_FUNCTION)

        when:
        def secondInjection = CgroupScriptUtils.injectCgroupSupport(firstInjection, MOCK_CGROUP_FUNCTION)

        then:
        secondInjection.count('nxf_mem_watch_cgroup()') == 2
    }

    @Unroll
    def "hasMemWatchFunction returns #expected for #input"() {
        expect:
        CgroupScriptUtils.hasMemWatchFunction(input) == expected

        where:
        input                             | expected
        'nxf_mem_watch() { echo test }'   | true
        'stuff\nnxf_mem_watch()\nmore'    | true
        'nxf_mem_watch(){'                | true
        '  nxf_mem_watch() '              | true
        'echo hello'                      | false
        'nxf_mem_watch_other()'           | false
        ''                                | false
        null                              | false
    }
}
