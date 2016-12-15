## Assumptions

This code is written with a few assumptions in mind to simplify implementation.

- Vote data is not required to be durable, so we store votes in-memory only. Vote data is reset when the service is restarted, but the service will allow votes on images already stored in Dropbox after restart.
- No pictures larger than 150MB. This service does not gracefully handle images larger than the allowed single-request limit for the Dropbox REST API. SMS might not allows a media file that big, regardless.
- No strategy for scaling or high-availability. There are no provisions for operating in a clustered/highly available environment. The service does not support coordination between multiple instances, nor does it support a shared data store.
- No requirements for API request latency. The event notification handlers in some cases initiaite and complete remote requests to Dropbox before returning. If the Burner webhook callbacks have a fixed response time requirement below what can be expected from Dropbox, then more of the Dropbox processing could be handled asynchronous to the REST API requests.

## Run

To run the application execute `sbt -DdropboxToken=<token> -DdropboxFolder=<folder> run` from the top directory. Substitute valid values for the Dropbox token for authentication and the folder in which to store images.

To submit and image, you can use cURL from the command line, like so:

`curl -v -X POST "http://localhost:9000/event" --header "Content-Type: application/json" -d "@image1.json"`

To vote, you can use: 

`curl -v -X POST "http://localhost:9000/event" --header "Content-Type: application/json" -d "@vote1.json"`

To retrieve the report, use:

`curl -v -X GET "http://localhost:9000/report"`

I have tested the application with a live Burner account and successfully triggered both image upload and voting from my cell phone running Burner.
