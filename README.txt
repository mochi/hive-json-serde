Gitification of http://code.google.com/p/hive-json-serde

with local patches for Mochi data friendliness.

Patches I added:
1. if the return value is of type JSONArray, I return a  json object 
that contain the jsonArray string representation. That way we could use get_json_object in the following way:

get_json_object(retVal,"$array[index you are looking for]")

enjoy
