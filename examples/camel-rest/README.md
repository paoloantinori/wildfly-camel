Camel REST Example
------------------

This example demonstrates how to write JAX-RS REST routes with JBoss Fuse on EAP.

The example demonstrates two methods for creating JAX-RS consumer endpoints using the [Camel REST DSL](http://camel.apache.org/rest-dsl.html)
and using the [CamelProxy](http://camel.apache.org/using-camelproxy.html) in conjunction with the EAP JAX-RS subsystem. These methods are alternatives to
using the CXFRS and Restlet component consumer endpoints **which are not currently supported by JBoss Fuse on EAP**.

A JAX-RS producer example is demonstrated with the [camel-restlet component](http://camel.apache.org/restlet.html).

Prerequisites
-------------

* Maven
* An application server with JBoss Fuse installed

Running the example
-------------------

To run the example.

1. Start the application server in standalone mode `${JBOSS_HOME}/bin/standalone.sh -c standalone-full.xml`
2. Build and deploy the project `mvn install -Pdeploy`
3. Browse to http://localhost:8080/example-camel-rest/customers

You should see a page titled 'Manage Customers'. This UI enables you to interact with the example JAX-RS REST services.

The exposed REST endpoints are:

| HTTP Method | URL   | Purpose |
|---|---|---|---|---|
| GET | /example-camel-rest/rest/customer  | Gets a list of customers |
| GET | /example-camel-rest/camel/customer/{id}  | Gets a specific customer |
| POST | /example-camel-rest/camel/customer  | Creates a new customer |
| PUT | /example-camel-rest/rest/customer  | Updates an existing customer |
| DELETE | /example-camel-rest/rest/customer  | Deletes all customers |
| DELETE | /example-camel-rest/rest/{id}  | Deletes a specific customer |

You may be wondering why some paths are written as `/example-camel-rest/camel` and others as `/example-camel-rest/rest`. This example demonstrates two methods of implementing Camel REST consumers. Requests made to paths under `/example-camel-rest/camel` are handled by the Camel REST DSL and requests made to paths `/example-camel-rest/rest` are handled by the EAP JAX-RS subsystem together with the CamelProxy.  


Testing Camel REST Consumers
----------------------------

Web UI
------

Browse to http://localhost:8080/example-camel-rest/customers.

Creating Customers
------------------

The web form allows us to interact with each REST endpoint. Enter a 'first name' and 'last name' into the provided fields and click Submit. A new customer is created. The WebUI sends POST request with a JSON representation of the customer detail to `/example-camel-rest/camel/customer`.

````json
{
  "firstName":"John",
  "lastName":"Doe"
}
````

The Camel REST DSL unmarshalls the JSON data to a Customer POJO and uses JPA to persist it. The type() DSL method performs the unmarshal.

````java
rest("/customer")
  .post()
    .type(Customer.class)
    .to("direct:createCustomer");
````

To enable this to work, the REST DSL is configured with an appropriate binding mode.

````java
restConfiguration().component("servlet").contextPath("/camel-example-rest/camel").port(8080).bindingMode(RestBindingMode.json);
````

__Updating Customers__

Now click on the link against the customer name you just created. The details are populated within the form fields. When this happens the web UI makes a HTTP GET request to `/example-camel-rest/camel/customer/1`. This results in a JSON representation of a Customer POJO being returned.


````json
{
  "id":"1",
  "firstName":"John",
  "lastName":"Doe"
}
````

Camel handles the conversion of POJOs to JSON for us. All we need to do is use the produces() method with the desired media type.

````java
rest("/customer")
  .get("/{id}")
    .produces(MediaType.APPLICATION_JSON)
    .to("direct:readCustomer")
````

Now change the data in the 'first name' and 'last name' form fields and click Submit. The web UI makes an HTTP PUT request to `/example-camel-rest/rest/customer/1` with JSON like the following.

````json
{
  "id":"1",
  "firstName":"Your modified first name",
  "lastName":"Your modified last name"
}
````

This time the request is handled by the EAP JAX-RS subsystem which has created a REST endpoint for the CustomerService interface.
````java
@Path("/customer")
public interface CustomerService {
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  Response updateCustomer(Customer customer);
}
````

Since the `updateCustomer` method is annotated with `@Consumes` the subsystem knows that the JSON should be unmarshalled to a Customer POJO.

When the `updateCustomer` method is invoked the `CamelProxy` results in the `direct:rest` route within `RestConsumerRouteBuilder` to run. Notice that a processor class
handles the REST responses by figuring out which methods were invoked.

````java
from("direct:rest")
 .process(new Processor() {
   @Override
   public void process(Exchange exchange) throws Exception {
       BeanInvocation beanInvocation = exchange.getIn().getBody(BeanInvocation.class);
       String methodName = beanInvocation.getMethod().getName();

       if (methodName.equals("getCustomers")) {
         ...
       } else if(methodName.equals("updateCustomer")) {
         ...
       }
       ...
     }
   }
````

In each of the if statement cases a respnonse is sent back to the client using a standard JAX-RS response builder.

````java
exchange.getOut().setBody(Response.ok().build());
````

__Deleting Customers__

Now click on the 'X' icon next to the customer to trigger a delete. The web UI makes a HTTP DELETE request to `/example-camel-rest/rest/customer/1`.


cURL
----

If you have access to cURL then you can run the following commands from a terminal session and get an insight into the HTTP request / response
data being sent to and from the server.

__Get Customers__
```
curl -v -X GET localhost:8080/example-camel-rest/rest/customer
```

__Create Customer__
```
curl -v -X POST -H "Content-Type: application/json" -d @src/main/resources/create-customer.json localhost:8080/example-camel-rest/camel/customer/
```

__Get Customer id 1__
```
curl -v -X GET -H "Content-Type: application/json" -d @src/main/resources/create-customer.json localhost:8080/example-camel-rest/camel/customer/1
```

__Update Customer__
```
curl -v -X PUT -H "Content-Type: application/json" -d @src/main/resources/update-customer.json localhost:8080/example-camel-rest/rest/customer/
```

__Unmodified Customer__
```
curl -v -X PUT -H "Content-Type: application/json" -d @src/main/resources/update-unmodified-customer.json localhost:8080/example-camel-rest/rest/customer/
```

__Delete Customer__
```
curl -v -X DELETE localhost:8080/example-camel-rest/rest/customer/1
```

__Delete Customers__
```
curl -v -X DELETE localhost:8080/example-camel-rest/rest/customer/
```

Testing Camel REST Producers
----------------------------

This example demonstrates how to use camel-restlet endpoints as clients for consuming RESTful services.

```java
from("timer://outputCustomers?period=30000")
.log("Updating customers.json")
.to("restlet://http://localhost:8080/example-camel-rest/rest/customer")
.setHeader(Exchange.FILE_NAME, constant("customers.json"))
.to("file:{{jboss.server.data.dir}}/customer-records/");
```

This route makes HTTP GET requests to a REST service running at http://localhost:8080/example-camel-rest/rest/customer (see above consumer examples). It
retrieves any customer records as a JSON string and writes the results to a `customers.json` file to `${JBOSS_HOME}/standalone/data/customer-records/`.

Undeploy
--------

To undeploy the example run `mvn clean -Pdeploy`.
