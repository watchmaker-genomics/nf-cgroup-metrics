package com.watchmakergenomics.cgroup

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.trace.TraceObserver
import nextflow.trace.TraceObserverFactory
import org.pf4j.Extension

@Extension
@Slf4j
@CompileStatic
class CgroupObserverFactory implements TraceObserverFactory {

    @Override
    Collection<TraceObserver> create(Session session) {
        final config = session.config.navigate('cgroup.metrics') as Map ?: [:]
        final enabled = config.enabled != false 

        if (!enabled) {
            log.info "Cgroup metrics plugin is disabled via configuration"
            return Collections.emptyList()
        }

        log.info "Initializing CgroupMetricsObserver for session"
        return Collections.singletonList(new CgroupMetricsObserver(session, config) as TraceObserver)
    }
}
