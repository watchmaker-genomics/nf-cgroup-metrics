# nf-cgroup-metrics

## Summary

A Nextflow plugin that replaces the default `/proc`-based memory tracking with cgroup-aware tracking for containerized tasks.

Nextflow's default memory tracking reads `/proc/<pid>/status`, which reports memory for the main process only. In containerized environments, this misses memory-mapped files (e.g. genome indices loaded by STAR, BWA, Bowtie2), page cache from I/O-heavy workloads, and container-wide memory enforced by the cgroup runtime. This plugin reads from the cgroup memory controller instead — the same accounting that the container runtime uses to enforce limits and trigger OOM kills.

Supports cgroup v1 and v2. Falls back to default `/proc` tracking if cgroups are unavailable.

## Get Started

### Requirements

- Nextflow >= 24.04.0
- Java 17 or later (for building from source)
- Linux with cgroup v1 or v2
- A containerized executor (Docker, AWS Batch, etc.)

### Install from source

```bash
git clone https://github.com/watchmaker-genomics/nf-cgroup-metrics.git
cd nf-cgroup-metrics
make install
```

This installs the plugin to `~/.nextflow/plugins/nf-cgroup-metrics-1.0.1/`.

### Configuration

Add to your `nextflow.config`:

```groovy
plugins {
    id 'nf-cgroup-metrics@1.0.1'
}
```

The plugin is enabled by default. Optional settings:

```groovy
cgroup {
    metrics {
        enabled = true    // set false to disable
        verbose = false   // set true for debug logging
    }
}
```

The plugin only modifies containerized tasks. Tasks running on the local executor without a container use default `/proc` tracking.

## Examples

### AWS Batch

```groovy
plugins {
    id 'nf-cgroup-metrics@1.0.1'
}

process {
    executor = 'awsbatch'
    queue = 'my-batch-queue'
}

aws.region = 'us-east-1'
```

### Checking that it works

Enable verbose logging and run a pipeline:

```groovy
cgroup {
    metrics {
        verbose = true
    }
}
```

```bash
nextflow run main.nf
```

The trace report fields `vmem`, `rss`, `peak_vmem`, and `peak_rss` will reflect container-wide memory usage rather than per-process values.

## How It Works

The plugin implements a `TraceObserver` that intercepts task submission. Before each containerized task is dispatched to the executor, it patches the `.command.run` wrapper script to inject a cgroup-aware memory tracking function (`nxf_mem_watch_cgroup`). This function runs in place of Nextflow's default `/proc`-based polling loop.

### Cgroup detection

The injected function auto-detects the cgroup version at runtime:

**Cgroup v2** (unified hierarchy):
- `memory.current` — current usage
- `memory.peak` — peak usage
- `memory.stat` (`anon`) — anonymous memory (RSS equivalent)

**Cgroup v1** (legacy):
- `memory.usage_in_bytes` — current usage
- `memory.max_usage_in_bytes` — peak usage
- `memory.stat` (`rss` + `rss_huge`) — resident memory

### Trace field mapping

| Trace Field | Default Source | Cgroup v1 Source | Cgroup v2 Source |
|---|---|---|---|
| `%mem` | `VmRSS / total` | `rss / total` | `anon / total` |
| `vmem` | `VmSize` | `memory.usage_in_bytes` | `memory.current` |
| `rss` | `VmRSS` | `memory.stat rss` | `memory.stat anon` |
| `peak_vmem` | max `VmSize` | `memory.max_usage_in_bytes` | `memory.peak` |
| `peak_rss` | max `VmRSS` | max cgroup RSS | max cgroup anon |

Context switches (`vol_ctxt`, `inv_ctxt`) remain process-based via `/proc`.

## Troubleshooting

Verify cgroup access inside your container:

```bash
# Check cgroup version
ls /sys/fs/cgroup/cgroup.controllers 2>/dev/null && echo "v2" || echo "v1 or none"
cat /proc/self/cgroup

# Check plugin installation
ls ~/.nextflow/plugins/nf-cgroup-metrics-1.0.1/

# Enable Nextflow debug mode
NXF_DEBUG=1 nextflow run main.nf
```

## License

Apache License 2.0. See [COPYING](COPYING).
