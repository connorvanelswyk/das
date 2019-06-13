#!/bin/bash

nodes=( $(curl -s -H 'API-Key: RNPF3WJYTFWF6C5FCBRLXZAFKZTLSOBU43CA' "https://api.vultr.com/v1/server/list" | \
  jq -r '.[] | select(.label != "Master Node") | .main_ip') )

# printf '%s\n' "${nodes[@]}"

if [ ${#nodes[@]} -eq 0 ]; then
  echo "No IPs returned by the vultr api!"
  exit 1
fi

for x in "${nodes[@]}"; do
  echo "sending to ${x}"
  ssh -o ConnectTimeout=10 -o StrictHostKeyChecking=no -i $HOME/vultr -T root@${x} 'cd chloe; git reset --hard; sudo rm logs/*; git checkout master; git fetch -p; git pull; pkill -f tail; pkill -f java;' &
done
echo
echo
echo "completed";
