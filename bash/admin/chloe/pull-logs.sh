
nodes=( $(curl -s -H 'API-Key: RNPF3WJYTFWF6C5FCBRLXZAFKZTLSOBU43CA' "https://api.vultr.com/v1/server/list" | \
  jq -r '.[] | select(.label != "Master Node") | .main_ip') )

# printf '%s\n' "${nodes[@]}"

if [ ${#nodes[@]} -eq 0 ]; then
  echo "No IPs returned by the vultr api!"
  exit 1
fi

# for linux
# date=`date +%Y-%m-%d -d "1 day ago"`

# for macos
date=`date -v-1d +%Y-%m-%d`
echo ${date}

# echo "sending [${COMMAND}] to all nodes...";
for x in "${nodes[@]}"; do
  echo "pulling all logs from ${date} for ${x}"
  mkdir -p ~/logs/${x}
  mkdir ~/logs/${x}/${date} && \
  scp -i $HOME/vultr root@${x}:~/chloe/logs/all${date}*.log ~/logs/${x}/${date} &
done
echo
echo
echo "completed";
