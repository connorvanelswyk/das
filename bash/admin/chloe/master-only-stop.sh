#!/usr/bin/env bash

echo
echo "stopping master..."

ip=( $(curl -s -H 'API-Key: RNPF3WJYTFWF6C5FCBRLXZAFKZTLSOBU43CA' "https://api.vultr.com/v1/server/list" | \
  jq -r '.[] | select(.label == "Master Node") | .main_ip') )

ssh -o ConnectTimeout=10 -o StrictHostKeyChecking=no -i $HOME/vultr root@${ip} 'sudo killall -1 java';
