#!/bin/bash
omero_server="$1"
omero_user="$(cat $2 | awk 'NR==2{print $0}')"
omero_password="$(cat $2 | awk 'NR==3{print $0}')"
to_create=$3
project_name_or_id=$4
dataset_name_or_id=$5

if [ "$to_create" = "both" ]; then
    # Create a project:
    project_name_or_id=$(omero obj -s ${omero_server} -u ${omero_user} -w ${omero_password} new Project name="${project_name_or_id}" | awk -F ":" 'END{print $NF}')
    echo "Just created the new project ${project_name_or_id}"
fi
if [ "$to_create" = "both" ] || [ "$to_create" = "dataset" ]; then
    dataset_name_or_id=$(omero obj -s ${omero_server} -u ${omero_user} -w ${omero_password} new Dataset name="${dataset_name_or_id}" | awk -F ":" 'END{print $NF}')
    echo "Just created the new dataset ${dataset_name_or_id}"
    omero obj -s ${omero_server} -u ${omero_user} -w ${omero_password} new ProjectDatasetLink parent=Project:${project_name_or_id} child=Dataset:${dataset_name_or_id}
fi
echo "Start upload"
omero import -s ${omero_server} -u ${omero_user} -w ${omero_password} --depth 1 -T Dataset:id:"${dataset_name_or_id}" output 2>&1
echo "Upload finished"
