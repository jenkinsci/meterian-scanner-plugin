#!/bin/bash

set -e
set -u
set -o pipefail

docker run -it                      \
           --workdir /home/         \
           --volume $(pwd)/:/home/  \
           meterian-scanner-plugin  \
           /bin/bash