#!/bin/bash

# while [[ $# -gt 0 ]]
# do
# key="$1"
#
# case $key in
#     -c|--command)
#     COMMAND="$2"
#     shift # past argument
#     shift # past value
#     ;;
#     *)    # unknown option
#     shift
#     ;;
# esac
# done

COMMAND="$*"

if [[ -z "${COMMAND}" ]]; then
  echo "command cannot be empty!"
  exit 1
fi

nodes=( $(curl -s -H 'API-Key: RNPF3WJYTFWF6C5FCBRLXZAFKZTLSOBU43CA' "https://api.vultr.com/v1/server/list" | \
  jq -r '.[] | select(.label != "Master Node") | .main_ip') )

# printf '%s\n' "${nodes[@]}"

if [ ${#nodes[@]} -eq 0 ]; then
  echo "No IPs returned by the vultr api!"
  exit 1
fi

# echo "sending [${COMMAND}] to all nodes...";
for x in "${nodes[@]}"; do
  echo "sending to ${x}"
  ssh -o ConnectTimeout=10 -o StrictHostKeyChecking=no -i $HOME/vultr -T root@${x} ${COMMAND} &
done
echo
echo
echo "completed";
