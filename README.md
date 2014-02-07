# simple-rps-service

The game Rock-Paper-Scissors implemented as a microservice that stores data in MongoDB and publishes events to RabbitMQ. The service has a simple restful HTTP API that allows creation of games and making moves.

Try it [here](http://simple-rps-service.herokuapp.com/)!

Built using the following:

* [Langohr](http://clojurerabbitmq.info/) as RabbitMQ client
* [Monger](http://clojuremongodb.info/) as MongoDB client
* [Cheshire](https://github.com/dakrone/cheshire) for JSON marshalling
* [Friend](https://github.com/cemerick/friend) for OpenID authentication
* The usual suspects: [Ring](https://github.com/ring-clojure/ring), [Compojure](https://github.com/weavejester/compojure), [Hiccup](https://github.com/weavejester/hiccup)...

Deployed on [Heroku](http://heroku.com) using [CloudAMQP](http://www.cloudamqp.com/) and [MongoHQ](http://www.mongohq.com/). Initally based on [leiningen heroku template](https://github.com/technomancy/lein-heroku) and [my previous explorations using Datomic and EventStore](https://github.com/jankronquist/rock-paper-scissors-in-clojure).

## Purpose

Used as part of a lab teaching how to build small services communicating using messaging. Not intended for production! :-)

MongoDB is simply used as a key/value storage and RabbitMQ as a simple publish/subscribe bus. 

## Limitations and known issues

Note: 

* This does not use event sourcing! MongoDB and RabbitMQ are not updated transactionally, instead first the message is sent and then MongoDB is update. This means that updates can be lost.
* Basically no input validation! :-)
* To keep it simple very few features of RabbitMQ is being used. Instead the message payload contains meta data.

# Running

# Locally

If you want to run the service locally you need:

1. Leiningen (`brew install leiningen`)
2. MongoDB (`brew install mongodb`)
3. RabbitMQ (`brew install rabbitmq`)

Run using: `lein run`

## Heroku

You need to [add three properties](https://toolbelt.heroku.com/) pointing out the AMQP and MongoDB URL's:

```bash
heroku config:set RABBITMQ_URL="<amqp_url>"
heroku config:set MONGODB_URL="<mongodb_url>"
heroku config:add SESSION_SECRET=$RANDOM_16_CHARS
```

Both URLs must contain username and password, port etc.

## Messages

### GameEndedEvent

Extends message specified by: https://github.com/johanhaleby/lab-service-registry/blob/master/LAB.md

Example:
```javascript
{
  "scores":{
     "player1":10,
     "player2":20
  },
  "result": "won",
  "winner": "player1",
  "loser": "player2"
}
```

### MoveMadeEvent

Sent when a player has made a move.

Example:
```javascript
{
  "player": "player1",
  "move": "rock"
}
```

## HTTP API

To make a move using curl:

	curl -u root:<password> -d player=<playerToMakeMove> -d move=<rock/paper/scissors> http://simple-rps-service.herokuapp.com/games/{gameId}

# License

Copyright @ 2014 Jan Kronquist

Distributed under the Eclipse Public License, the same as Clojure.
