#!/usr/bin/env nextflow

/*
 * Validation pipeline for nf-cgroup-metrics plugin
 * Tests that the plugin loads and modifies task scripts correctly
 */

process MEMORY_TEST {
    container 'ubuntu:22.04'

    output:
    path 'result.txt'

    script:
    """
    # Create a test file with random data
    dd if=/dev/urandom of=testfile.dat bs=1M count=100

    # Compress and decompress multiple times to generate CPU/memory activity
    gzip testfile.dat
    gunzip testfile.dat.gz
    gzip testfile.dat
    gunzip testfile.dat.gz
    gzip testfile.dat
    gunzip testfile.dat.gz
    gzip testfile.dat
    gunzip testfile.dat.gz

    echo "Validation complete" > result.txt
    """
}

workflow {
    MEMORY_TEST()
}
