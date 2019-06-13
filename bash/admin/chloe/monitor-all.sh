#!/bin/bash

pkill -f tail;

ip=( $(curl -s -H 'API-Key: RNPF3WJYTFWF6C5FCBRLXZAFKZTLSOBU43CA' "https://api.vultr.com/v1/server/list" | \
  jq -r '.[] | select(.label == "Master Node") | .main_ip') )

nodes=( $(curl -s -H 'API-Key: RNPF3WJYTFWF6C5FCBRLXZAFKZTLSOBU43CA' "https://api.vultr.com/v1/server/list" | \
  jq -r '.[] | select(.label != "Master Node") | .main_ip') )

cmd="multitail --retry-all -P a -M 15000 -mb 1MB -cT ANSI -wh 16 -l 'ssh -t -t -o ConnectTimeout=10 -o StrictHostKeyChecking=no -o ServerAliveInterval=120 -i $HOME/vultr root@${ip} \"tail -f chloe/logs/all.log\"' "
y=1


for x in "${nodes[@]}"; do
  if [ "$y" == 1 ]; then
    cmd="$cmd -cT ANSI -l 'ssh -t -t -o ConnectTimeout=10 -o StrictHostKeyChecking=no -o ServerAliveInterval=120 -i $HOME/vultr root@${x} \"tail -f chloe/logs/all.log\"' "
  else
    cmd="$cmd -cT ANSI -L 'ssh -t -t -o ConnectTimeout=10 -o StrictHostKeyChecking=no -o ServerAliveInterval=120 -i $HOME/vultr root@${x} \"tail -f chloe/logs/all.log\"' "
  fi
  ((y++))
done

eval $cmd
