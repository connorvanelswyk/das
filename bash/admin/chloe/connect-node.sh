#!/usr/bin/env bash

node_ip="$*"

if [[ -z "${node_ip}" ]]; then
  echo "node ip cannot be empty!"
  exit 1
fi

ssh -o ConnectTimeout=10 -o StrictHostKeyChecking=no -i $HOME/vultr root@${node_ip}
