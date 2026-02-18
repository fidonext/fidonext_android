#!/usr/bin/env bash

branch=$(git rev-parse --abbrev-ref HEAD)
repo=$(git remote get-url origin)
text="$@"

echo "Commiting and pushing '$text' into $branch ..."
echo "$repo"

sleep 1

git add .
git commit -a -m "$text"
git push origin $branch


echo "DONE"
exit 0