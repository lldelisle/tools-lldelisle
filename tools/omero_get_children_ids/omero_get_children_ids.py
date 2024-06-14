import argparse
import json

from omero.gateway import BlitzGateway


def get_omero_credentials(config_file):
    if config_file is None:  # IDR connection
        omero_username = "public"
        omero_password = "public"
    else:  # other omero instance
        with open(config_file) as f:
            cfg = json.load(f)
            omero_username = cfg["username"]
            omero_password = cfg["password"]

            if omero_username == "" or omero_password == "":
                omero_username = "public"
                omero_password = "public"
    return (omero_username, omero_password)


def recursive_get_children_id(parent_object, final_object_type, get_name):
    output = []
    if parent_object.OMERO_CLASS == 'WellSample':
        if get_name:
            return [f"{parent_object.getImage().id}\t{parent_object.getImage().getName()}"]
        else:
            return [parent_object.getImage().id]
    for children in parent_object.listChildren():
        if children.OMERO_CLASS == final_object_type.title():
            if get_name:
                output.append(f"{children.id}\t{children.getName()}")
            else:
                output.append(children.id)
        else:
            # We need to go one step further
            output += recursive_get_children_id(children, final_object_type, get_name)
    return output


def get_children_ids(parent_object_type,
                     omero_id,
                     final_object_type,
                     get_name,
                     omero_username,
                     omero_password,
                     omero_host="idr.openmicroscopy.org",
                     omero_secured=False):
    # Connect to omero:
    with BlitzGateway(
        omero_username, omero_password, host=omero_host, secure=omero_secured
    ) as conn:
        # Retrieve omero object
        parent_object = conn.getObject(parent_object_type.title(), omero_id)
        return recursive_get_children_id(parent_object, final_object_type, get_name)


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("-oh", "--omero-host", type=str,
                   default="idr.openmicroscopy.org")
    p.add_argument("--omero-secured", action="store_true", default=True)
    p.add_argument("-cf", "--config-file", dest="config_file",
                   default=None)
    p.add_argument("--parent-object-type", dest="parent_object_type",
                   type=str, default=None, required=True)
    p.add_argument("--omero-id", dest="omero_id",
                   type=int, default=None, required=True)
    p.add_argument("--final-object-type", dest="final_object_type",
                   type=str, default=None, required=True)
    p.add_argument("--get-name", dest="get_name",
                   action="store_true", default=False)
    p.add_argument("--output", type=str, default=None, required=True)
    args = p.parse_args()
    children_ids = get_children_ids(
        args.parent_object_type,
        args.omero_id,
        args.final_object_type,
        args.get_name,
        *get_omero_credentials(args.config_file),
        omero_host=args.omero_host,
        omero_secured=args.omero_secured,
    )
    with open(args.output, 'w') as fo:
        fo.write('\n'.join([str(id) for id in children_ids]))
        fo.write('\n')
