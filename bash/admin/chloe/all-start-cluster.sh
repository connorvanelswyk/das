#!/usr/bin/env bash

echo
echo "starting master..."

ip=( $(curl -s -H 'API-Key: RNPF3WJYTFWF6C5FCBRLXZAFKZTLSOBU43CA' "https://api.vultr.com/v1/server/list" | \
  jq -r '.[] | select(.label == "Master Node") | .main_ip') )

ssh -o ConnectTimeout=10 -o StrictHostKeyChecking=no -i $HOME/vultr root@${ip} 'bash ~/chloe/bash/start-master.sh';

echo
echo "starting all worker nodes..."
echo

nodes=( $(curl -s -H 'API-Key: RNPF3WJYTFWF6C5FCBRLXZAFKZTLSOBU43CA' "https://api.vultr.com/v1/server/list" | \
  jq -r '.[] | select(.label != "Master Node") | .main_ip') )

# printf '%s\n' "${nodes[@]}"

if [ ${#nodes[@]} -eq 0 ]; then
  echo "No IPs returned by the vultr api!"
  exit 1
fi

echo
for x in "${nodes[@]}"; do
  echo "starting ${x}"
  ssh -o ConnectTimeout=10 -o StrictHostKeyChecking=no -i $HOME/vultr -T root@${x} 'bash ~/chloe/bash/start-worker.sh' &
done
wait

echo
echo "all worker nodes (${#nodes[@]}) started"
