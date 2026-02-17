package com.watchmakergenomics.cgroup

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.plugin.BasePlugin
import org.pf4j.PluginWrapper

@Slf4j
@CompileStatic
class CgroupMetricsPlugin extends BasePlugin {

    CgroupMetricsPlugin(PluginWrapper wrapper) {
        super(wrapper)
    }

    @Override
    void start() {
        super.start()
        log.info "Cgroup Metrics Plugin v${wrapper.descriptor.version} - Loaded successfully"
        log.debug "Plugin will modify task scripts to use cgroup-based memory tracking"
    }

    @Override
    void stop() {
        log.info "Cgroup Metrics Plugin - Shutting down"
        super.stop()
    }
}
