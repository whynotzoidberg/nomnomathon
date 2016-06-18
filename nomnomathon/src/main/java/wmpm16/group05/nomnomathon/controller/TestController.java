package wmpm16.group05.nomnomathon.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.log4j.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import wmpm16.group05.nomnomathon.beans.StoreOrderBean;
import wmpm16.group05.nomnomathon.domain.OrderRequest;
import wmpm16.group05.nomnomathon.domain.RestaurantData;
import wmpm16.group05.nomnomathon.mocked.OrderRequestAnswer;
import wmpm16.group05.nomnomathon.models.Customer;
import wmpm16.group05.nomnomathon.models.CustomerRepository;
import wmpm16.group05.nomnomathon.models.OrderInProcess;
import wmpm16.group05.nomnomathon.models.OrderInProcessRepository;
import wmpm16.group05.nomnomathon.models.OrderState;
import wmpm16.group05.nomnomathon.routers.NomNomConstants;

@RestController
public class TestController {
	private Logger log = Logger.getLogger(TestController.class);

	@Autowired
	private CamelContext context;

	@Autowired
	CustomerRepository customerRepository;
	
	@Autowired
	ConfigurableApplicationContext springContext;
	
	@Autowired
	StoreOrderBean orderBean;
	
	@Autowired
	OrderInProcessRepository orderRepository;

	@RequestMapping("/test/EnrichOrder")
	public OrderRequest testEnrichCustomer() {

		Optional<Customer> opcustomer = customerRepository.findOneByUserNameAndPassword("bernd", "nomnom");
		log.debug("Found customer: " + (opcustomer.isPresent() ? opcustomer.get() : "No customer found - exiting"));

		if (!opcustomer.isPresent()) {
			log.error("No customer available");
			return null;

		}
		Customer customer = opcustomer.get();
		Optional<Long> oprestaurantid = Optional.ofNullable(null);
		List<String> disheslist = new ArrayList<>();

		OrderRequest orderReq = new OrderRequest();
		orderReq.setUserId(Optional.of(customer.getId()));
		orderReq.setRestaurantId(oprestaurantid);
		orderReq.setDishes(disheslist);

		ProducerTemplate template = context.createProducerTemplate();
		OrderRequest response = (OrderRequest) template.requestBody("direct:enrichCustomerData", orderReq);

		return response;

	}

	@RequestMapping("/test/testRestaurantStatus")
	public void testRestaurantStatus() {
		log.debug("START TestRestaurantStatus");

		try {
			context.addRoutes(new RouteBuilder() {

				@Override
				public void configure() throws Exception {
					from("direct:testRestaurantStatus").marshal().json(JsonLibrary.Jackson)
							.setHeader("Exchange.HTTP_METHOD", constant("GET"))
							.to("http://localhost:8080/external/restaurants/123/234/capacity");

				}
			});
			log.debug("ROUTE added");
		} catch (Exception e) {

			log.error(e);
		}
		OrderInProcess order = new OrderInProcess();

		ProducerTemplate template = context.createProducerTemplate();
		template.requestBody("direct:testRestaurantStatus", order);

		log.debug("END TestRestaurantStatus");

	}

	@RequestMapping("/test/testRestaurantOrder")
	public OrderRequestAnswer testRestaurantOrder() {
		log.debug("START TestRestaurantOrder");

		try {
			context.addRoutes(new RouteBuilder() {

				@Override
				public void configure() throws Exception {
					from("direct:testRestaurantOrder")
							.to("log:wmpm16.group05.nomnomathon.controller.TestController.TestOrder?level=DEBUG")
							.setHeader(Exchange.HTTP_URI,
									simple("http://localhost:8080/external/restaurants/${body.restaurantId}/order"))
							.marshal().json(JsonLibrary.Jackson).setHeader(Exchange.HTTP_METHOD, constant("POST"))
							.setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
							.to("log:wmpm16.group05.nomnomathon.controller.TestController.TestOrder?level=DEBUG")
							.to("http://dummyhost").unmarshal().json(JsonLibrary.Jackson, OrderRequestAnswer.class)
							.to("log:wmpm16.group05.nomnomathon.controller.TestController.TestOrder?level=DEBUG");

				}
			});
			log.debug("ROUTE added");
		} catch (Exception e) {

			log.error(e);
		}
		OrderInProcess order = createOrderInProcess();

		log.debug(order);

		ProducerTemplate template = context.createProducerTemplate();
		OrderRequestAnswer answer = (OrderRequestAnswer) template.requestBody("direct:testRestaurantOrder", order);

		log.debug("END TestRestaurantOrder");

		return answer;

	}

	@RequestMapping("/test/requestcapacity")
	public void requestCapacity() {
		log.debug("START requestcapacity");
		ProducerTemplate template = context.createProducerTemplate();

		OrderInProcess order = createOrderInProcess();
		
		template.requestBody("direct:storeOrder", order);

		List<RestaurantData> restaurants = new ArrayList<>();
		
		RestaurantData rest1 = new RestaurantData();
		rest1.set_id(123);
		restaurants.add(rest1);
		
		RestaurantData rest2 = new RestaurantData();
		rest2.set_id(234);
		restaurants.add(rest2);
		
		HashMap<String, Object> headers = new HashMap<>();
		headers.put("orderId", 123L);

		
		template.requestBodyAndHeaders("direct:requestCapacity", restaurants, headers);

		log.debug("END requestcapacity");
	}

	private OrderInProcess createOrderInProcess() {
		OrderInProcess order = new OrderInProcess();
		order.addDish("Sushi");
		order.addDish("Pizza");
		order.setState(OrderState.CREATED);
		return order;
	}

	@RequestMapping("/test/creditCard")
	@ResponseBody
	public Object testCreditCard() {
		ProducerTemplate template = context.createProducerTemplate();

		HashMap<String, Object> headers = new HashMap<>();
		headers.put("creditCard", "123456789");
		headers.put("amount",123.34d);

		Object ret = template.requestBodyAndHeaders("direct:checkCreditCard", null, headers);
		return ret;
	}

	@RequestMapping("/test/updateOrder")
	@ResponseBody
	public Object testUpdateOrder() {
		ProducerTemplate template = context.createProducerTemplate();

		OrderInProcess order = createOrderInProcess();

		Exchange exchange = new DefaultExchange(template.getCamelContext());

		exchange.getIn().setBody(order);
		template.send("direct:storeOrder", exchange);

		Object orderId = exchange.getIn().getHeader(NomNomConstants.HEADER_ORDER_ID);
		Object o = template.requestBodyAndHeader("direct:loadOrder", null, "orderId", orderId);

		System.out.println(o);

		order = (OrderInProcess) o;
		order.setRestaurantId(444L);
		order.setState(OrderState.FULLFILLED);

		Object ret = template.sendBody("direct:updateOrder", ExchangePattern.InOut, order);
		return ret;
	}

}
