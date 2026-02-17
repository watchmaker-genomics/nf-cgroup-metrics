# Contributing

## Bug Reports and Feature Requests

Please open an issue on GitHub. For bug reports, include:

- Nextflow version (`nextflow -v`)
- Plugin version
- Executor type (AWS Batch, Docker, etc.)
- Cgroup version (v1 or v2)
- Relevant logs (run with `cgroup.metrics.verbose = true` and `NXF_DEBUG=1`)

## Development

### Prerequisites

- Java 17+
- Nextflow >= 24.04.0

### Build and test

```bash
make assemble    # compile and package
make test        # run unit tests
make install     # install to ~/.nextflow/plugins/
```

### Running the validation pipeline

The `validation/` directory contains an end-to-end test pipeline:

```bash
nextflow run validation/main.nf -c validation/nextflow.config
```

### Code style

- Groovy source follows standard Nextflow plugin conventions
- Shell scripts use `set -euo pipefail` where appropriate
- Tests use Spock

## Pull Requests

This plugin is maintained by Watchmaker Genomics. We welcome PRs for bug fixes and improvements. Before starting work on a significant change, please open an issue to discuss the approach.

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
