akka-persistence-event-sourcing
==============================

Example project with a simple CRUD REST API to a domain model persisted using akka-persistence with event sourcing.

To start the spray-can server from sbt:
> re-start

To stop:
> re-stop


### Project summary
- further developed template for prototyping, based on https://github.com/ScalaConsultants/akka-persistence-eventsourcing
- simple CRUD REST API with endpoints for user ( as found in the original template ) and endpoints for POST /children(create) and GET /children (index)
- Spray "actor per request" model inspired by spray-actor-per-request Activator template and Cameo Pattern from Jamie Allen's "Effective Akka"
- simple domain model representing a Children and User
- akka-persistence event sourcing used to track changes to the domain model

### Learning
- After adding the GET /children route and implementing it as a command 'GetAll', we became aware of the issue that firing ask futures to every leaf children actors poses: 
This probably won't scale. Further discovery is needed to implement the 'Q' part of CQRS properly. Current assumption: 
We need a persistent actor that holds the state of all active children internally to be queried, which is updated by the internal event bus. 

