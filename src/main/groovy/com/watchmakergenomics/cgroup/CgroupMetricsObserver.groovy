package com.watchmakergenomics.cgroup

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import nextflow.Session
import nextflow.processor.TaskHandler
import nextflow.processor.TaskRun
import nextflow.trace.TraceObserver
import nextflow.trace.TraceRecord
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Slf4j
@CompileStatic
class CgroupMetricsObserver implements TraceObserver {

    private final Session session
    private final Map config
    private final String cgroupMemWatchFunction
    private final boolean verbose

    CgroupMetricsObserver(Session session, Map config) {
        this.session = session
        this.config = config
        this.verbose = config.verbose == true
        this.cgroupMemWatchFunction = loadCgroupMemWatchFunction()
    }

    private String loadCgroupMemWatchFunction() {
        try {
            final stream = getClass().getResourceAsStream('/nextflow/observer/cgroup-mem-watch.sh')
            if (!stream) {
                throw new RuntimeException("Failed to find cgroup-mem-watch.sh in plugin resources")
            }
            return stream.text
        } catch (Exception e) {
            log.error "Failed to load cgroup memory watch function", e
            throw e
        }
    }

    @Override
    void onProcessSubmit(TaskHandler handler, TraceRecord trace) {
        try {
            modifyTaskScript(handler.task)
        } catch (Exception e) {
            log.warn "Failed to modify task script for cgroup tracking: ${handler.task.name} - ${e.message}"
            if (verbose) {
                log.debug "Cgroup modification error details", e
            }
            // Don't fail the task - fall back to default tracking
        }
    }

    private void modifyTaskScript(TaskRun task) {
        if (!task.container) {
            log.debug "Task ${task.name} not using container, skipping cgroup metrics injection"
            return
        }

        final wrapperFile = task.workDir.resolve(TaskRun.CMD_RUN)

        if (!Files.exists(wrapperFile)) {
            log.debug "Wrapper file not found for task ${task.name}, skipping modification"
            return
        }

        String script = wrapperFile.text

        if (!CgroupScriptUtils.hasMemWatchFunction(script)) {
            log.debug "Task ${task.name} does not use memory tracking, skipping modification"
            return
        }

        final modifiedScript = CgroupScriptUtils.injectCgroupSupport(script, cgroupMemWatchFunction)

        if (modifiedScript == script) {
            log.warn "Failed to replace nxf_mem_watch function in ${task.name}"
            return
        }

        def tempFile = wrapperFile.resolveSibling(".command.run.tmp.${System.nanoTime()}")
        Files.write(tempFile, modifiedScript.getBytes("UTF-8"))
        try {
            Files.move(tempFile, wrapperFile,
                       StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING)
        } catch (Exception e) {
            // S3 and other remote filesystems don't support atomic move.
            // Write directly — S3 PUTs are already atomic at the object level.
            Files.delete(tempFile)
            Files.write(wrapperFile, modifiedScript.getBytes("UTF-8"))
        }

        log.debug "Injected cgroup memory tracking into ${task.name}"
    }

    @Override
    boolean enableMetrics() { return true }
}
