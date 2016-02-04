# Messeji API

## Send messages
### Request
**Route**: `/send`

**Method**: `POST`

**Headers**: `"X-Hello-Sense-Id": <sense_id>`

**Body**: `Messeji.Message`


### Response
**Status**: `200 OK`

**Body**: `Messeji.Message` object. Same as the request, but with a `messageId`.


### Description
Send the message to the specified Sense. This is asynchronous, so the response from this endpoint
may be received before the message is delivered (if the message is ever delivered).
To get status updates on message delivery, use the `messageId` from the response
when querying the `/status` endpoint.


## Get message status
### Request
**Route**: `/status/<messageId>`

**Method**: `GET`


### Response
**Status**: `200 OK`

**Body**: `Messeji.MessageStatus`


### Description
Get the current status for the given `messageId`. See the [documentation](https://github.com/hello/proto/tree/master/messeji)
to understand the different states of the message status.


## Receive messages
### Request
**Route**: `/receive`

**Medhod**: `POST`

**Headers**: `"X-Hello-Sense-Id": <sense_id>`

**Body**: `Messeji.ReceiveMessageRequest` (signed using encryption key)


### Response
**Status**: `200 OK`

**Body**: `Messeji.BatchMessage` (signed using encryption key)


### Description
Receive all unexpired, unacknowledged messages for this Sense ascending (earliest first) order. Relative ordering of messages for each senderId is preserved, but not necessarily absolute ordering between different senders.

In case of a server-side timeout, the server responds with an empty list of messages.

**Ack and ye shall receive.** The message ID’s from this response are used in the next POST to this endpoint, to let the server know which messages have already been “seen.” Once Sense has sent a `ReceiveMessageRequest` to the server and gotten a successful (2xx) response, the server guarantees that Sense will never receive those messages again.

The protobuf in the request and response are signed in the same manner as in Suripu Service’s batch endpoint.
