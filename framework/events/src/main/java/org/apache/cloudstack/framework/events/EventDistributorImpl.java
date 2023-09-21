package org.apache.cloudstack.framework.events;

import com.cloud.utils.component.ManagerBase;
import org.apache.log4j.Logger;

import javax.annotation.PostConstruct;
import java.util.List;

public class EventDistributorImpl extends ManagerBase implements EventDistributor {
    private static final Logger LOGGER = Logger.getLogger(EventDistributorImpl.class);

    public void setEventBusses(List<EventBus> eventBusses) {
        this.eventBusses = eventBusses;
    }

    List<EventBus> eventBusses;

    @PostConstruct
    public void init() {
        for (EventBus bus : eventBusses) {
            try {
                bus.publish(new Event("server", "NONE","starting", "server", "NONE"));
            } catch (EventBusException e) {
                LOGGER.debug(String.format("no publish for bus %s", bus.getClass().getName()), e);
            }
        }
    }

}
