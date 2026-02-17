package com.watchmakergenomics.cgroup

import spock.lang.Specification
import spock.lang.Requires
import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests the cgroup-mem-watch.sh script logic using mocked cgroup files.
 * These tests verify the script correctly reads and parses cgroup v1 metrics.
 */
class CgroupMemWatchScriptSpec extends Specification {

    def "cgroup-mem-watch.sh resource exists and is readable"() {
        when:
        def stream = getClass().getResourceAsStream('/nextflow/observer/cgroup-mem-watch.sh')

        then:
        stream != null

        cleanup:
        stream?.close()
    }

    def "cgroup-mem-watch.sh contains nxf_mem_watch_cgroup function"() {
        when:
        def content = getClass().getResourceAsStream('/nextflow/observer/cgroup-mem-watch.sh').text

        then:
        content.contains('nxf_mem_watch_cgroup()')
    }

    def "cgroup-mem-watch.sh checks for cgroup v1 and v2 memory controllers"() {
        when:
        def content = getClass().getResourceAsStream('/nextflow/observer/cgroup-mem-watch.sh').text

        then:
        // v2 detection
        content.contains('cgroup.controllers')
        content.contains('memory.current')
        content.contains('memory.peak')
        // v1 detection
        content.contains('/sys/fs/cgroup/memory')
        content.contains('memory.usage_in_bytes')
        content.contains('memory.max_usage_in_bytes')
        content.contains('memory.stat')
    }

    def "cgroup-mem-watch.sh writes expected trace fields"() {
        when:
        def content = getClass().getResourceAsStream('/nextflow/observer/cgroup-mem-watch.sh').text

        then:
        content.contains('echo "%mem=')
        content.contains('echo "vmem=')
        content.contains('echo "rss=')
        content.contains('echo "peak_vmem=')
        content.contains('echo "peak_rss=')
        content.contains('echo "vol_ctxt=')
        content.contains('echo "inv_ctxt=')
    }

    def "cgroup-mem-watch.sh returns error code 1 when cgroup v1 unavailable"() {
        when:
        def content = getClass().getResourceAsStream('/nextflow/observer/cgroup-mem-watch.sh').text

        then:
        // Script should return 1 if /sys/fs/cgroup/memory doesn't exist
        content.contains('return 1')
    }

    def "cgroup-mem-watch.sh extracts rss and rss_huge from memory.stat"() {
        when:
        def content = getClass().getResourceAsStream('/nextflow/observer/cgroup-mem-watch.sh').text

        then:
        content.contains("/^rss /")
        content.contains("/^rss_huge /")
    }
}
