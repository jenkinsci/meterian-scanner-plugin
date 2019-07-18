#!/bin/bash

set -e
set -u
set -o pipefail

cd ..
docker build                                                       \
       --build-arg METERIAN_API_TOKEN=${METERIAN_API_TOKEN}        \
       --build-arg METERIAN_GITHUB_TOKEN=${METERIAN_GITHUB_TOKEN}  \
       -t meterian-scanner-plugin                                  \
       -f docker/Dockerfile . || (true && cd -)
