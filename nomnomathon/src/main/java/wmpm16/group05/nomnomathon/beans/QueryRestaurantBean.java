package wmpm16.group05.nomnomathon.beans;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import org.apache.camel.Exchange;
import org.apache.camel.component.mail.MailBinding;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;
import wmpm16.group05.nomnomathon.domain.Menu;
import wmpm16.group05.nomnomathon.domain.RestaurantData;
import wmpm16.group05.nomnomathon.models.Dish;
import wmpm16.group05.nomnomathon.models.OrderInProcess;
import wmpm16.group05.nomnomathon.models.OrderState;
import wmpm16.group05.nomnomathon.routers.RESTRouter;

import java.util.ArrayList;
import java.util.List;


/**
 * Created by Agnes on 21.05.16.
 */
@Component
public class QueryRestaurantBean {

    public void process(Exchange exchange) {
    	String uri = "http://localhost:8080/external/restaurants/%s/capacity";
        ArrayList<RestaurantData> restaurants = exchange.getIn().getBody(ArrayList.class);
        List<String> dishesInOrder = exchange.getIn().getHeader(RESTRouter.HEADER_DISHES_ORDER, ArrayList.class);
        List<String> restaurantIds = new ArrayList<>();
        List<String> dishesToDeliver = new ArrayList<>();
        for (String d : dishesInOrder) {
            for (RestaurantData r : restaurants) {
                List<Menu> menu = r.getMenu();
                List<String> dishes = new ArrayList<>();
                for (Menu m : menu) {
                    dishes.add(m.getName());
                }
                if (dishes.contains(d)) {
                    dishesToDeliver.add(d);
                    if (!restaurantIds.contains(r.get_id())) {
                    	/* Add a restaurant uri as String e.g. "http://localhost:8080/external/restaurants/2/capacity" */
                        restaurantIds.add(String.format(uri,r.get_id()));
                    }
                } else {
                    exchange.getIn().setHeader("orderState", OrderState.REJECTED_NO_RESTAURANTS);
                }
            }
        }

        if (exchange.getIn().getHeader("orderState") != OrderState.REJECTED_NO_RESTAURANTS) {
            exchange.getIn().setHeader("orderState", OrderState.ENRICHED);
        }
        /*contains all restaurant ids for capacity check*/
        exchange.getIn().setHeader("restaurants", restaurantIds);
   }

}