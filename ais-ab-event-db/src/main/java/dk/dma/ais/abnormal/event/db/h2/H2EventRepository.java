/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */

package dk.dma.ais.abnormal.event.db.h2;

import com.google.inject.Inject;
import dk.dma.ais.abnormal.event.db.EventRepository;
import dk.dma.ais.abnormal.event.db.domain.Behaviour;
import dk.dma.ais.abnormal.event.db.domain.CourseOverGroundEvent;
import dk.dma.ais.abnormal.event.db.domain.Event;
import dk.dma.ais.abnormal.event.db.domain.ShipSizeOrTypeEvent;
import dk.dma.ais.abnormal.event.db.domain.SpeedOverGroundEvent;
import dk.dma.ais.abnormal.event.db.domain.SuddenSpeedChangeEvent;
import dk.dma.ais.abnormal.event.db.domain.TrackingPoint;
import dk.dma.ais.abnormal.event.db.domain.Vessel;
import org.apache.commons.lang.StringUtils;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.ServiceRegistryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Date;
import java.util.List;

/**
 * H2EventRepository is an implementation of the EventRepository interface which
 * manages persistent Event objects in an H2 database (accessed via Hibernate).
 */
@SuppressWarnings("JpaQlInspection")
public class H2EventRepository implements EventRepository {

    private static final Logger LOG = LoggerFactory.getLogger(H2EventRepository.class);
    {
        LOG.info(this.getClass().getSimpleName() + " created (" + this + ").");
    }

    private final SessionFactory sessionFactory;
    private final boolean readonly;

    public static SessionFactory newSessionFactory(File dbFilename) {
        LOG.debug("Loading Hibernate configuration.");

        Configuration configuration = new Configuration()
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", buildConnectionUrl(dbFilename))
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.default_schema", "PUBLIC")
                .setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
                        //.setProperty("hibernate.show_sql", "true")
                .setProperty("hibernate.hbm2ddl.auto", "update")
                .setProperty("hibernate.order_updates", "true")
                .addAnnotatedClass(CourseOverGroundEvent.class)
                .addAnnotatedClass(SpeedOverGroundEvent.class)
                .addAnnotatedClass(ShipSizeOrTypeEvent.class)
                .addAnnotatedClass(SuddenSpeedChangeEvent.class)
                .addAnnotatedClass(Vessel.class)
                .addAnnotatedClass(Behaviour.class)
                .addAnnotatedClass(TrackingPoint.class);
        ServiceRegistryBuilder serviceRegistryBuilder = new ServiceRegistryBuilder();
        serviceRegistryBuilder.applySettings(configuration.getProperties());
        ServiceRegistry serviceRegistry = serviceRegistryBuilder.buildServiceRegistry();

        LOG.info("Starting Hibernate.");
        SessionFactory sessionFactory = configuration.buildSessionFactory(serviceRegistry);
        LOG.info("Hibernate started.");

        return sessionFactory;
    }

    @Inject
    public H2EventRepository(SessionFactory sessionFactory, boolean readonly) {
        this.readonly = readonly;
        this.sessionFactory = sessionFactory;
    }

    @Override
    protected void finalize() {
        LOG.info("Closing database session factory.");
        sessionFactory.close();
    }

    private static String buildConnectionUrl(File dbFilename) {
        StringBuffer connectionUrl = new StringBuffer();
        connectionUrl.append("jdbc:h2:");
        connectionUrl.append(dbFilename.getAbsolutePath());
        connectionUrl.append(";");
        connectionUrl.append("TRACE_LEVEL_FILE=0");
        connectionUrl.append(";");
        connectionUrl.append("TRACE_LEVEL_SYSTEM_OUT=1");
        LOG.debug("Using connectionUrl=" + connectionUrl);
        return connectionUrl.toString();
    }

    private Session getSession() {
        Session session = sessionFactory.openSession();
        if (readonly) {
            session.setDefaultReadOnly(true);
        }
        return session;
    }

    @Override
    public List<String> getEventTypes() {
        Session session = getSession();

        List events = null;
        try {
            Query query = session.createQuery("SELECT DISTINCT e.class AS c FROM Event e ORDER BY c");
            events = query.list();
        } finally {
            session.close();
        }

        return events;
    }

    @Override
    public void save(Event event) {
        Session session = getSession();
        try {
            session.beginTransaction();
            session.saveOrUpdate(event);
            session.getTransaction().commit();
        } finally {
            session.close();
        }
    }

    @Override
    public Event getEvent(long eventId) {
        Event event;
        Session session = getSession();
        try {
            event = (Event) session.get(Event.class, eventId);
        } finally {
            session.close();
        }
        return event;
    }

    @Override
    public List<Event> findEventsByFromAndToAndTypeAndVesselAndArea(Date from, Date to, String type, String vessel, Double north, Double east, Double south, Double west) {
        Session session = getSession();

        boolean usesFrom = false, usesTo = false, usesType = false, usesVessel = false, usesArea = false;

        List events = null;
        try {
            StringBuilder hql = new StringBuilder();

            if (north != null && east != null && south != null && west != null) {
                hql.append("SELECT DISTINCT e FROM Event e LEFT JOIN e.behaviour AS b LEFT JOIN b.trackingPoints AS tp WHERE latitude<:north AND latitude>:south AND longitude<:east AND longitude>:west AND ");
                usesArea = true;
            } else {
                hql.append("SELECT e FROM Event e WHERE ");
            }

            // from
            if (from != null) {
                hql.append("(e.startTime >= :from OR e.endTime >= :from) AND ");
                usesFrom = true;
            }

            // to
            if (to != null) {
                hql.append("(e.startTime <= :to OR e.endTime <= :to) AND ");
                usesTo = true;
            }

            // type
            if (! StringUtils.isBlank(type)) {
                hql.append("TYPE(e) IN (:classes) AND ");
                usesType = true;
            }

            // vessel
            if (! StringUtils.isBlank(vessel)) {
                hql.append("(");
                hql.append("e.behaviour.vessel.callsign LIKE :vessel OR ");
                hql.append("e.behaviour.vessel.name LIKE :vessel OR ");
                try {
                    Long vesselAsLong = Long.valueOf(vessel);
                    hql.append("e.behaviour.vessel.mmsi = :vessel OR ");
                    hql.append("e.behaviour.vessel.imo = :vessel OR ");
                } catch (NumberFormatException e) {
                }
                hql.replace(hql.length()-3, hql.length(), ")"); // "OR " -> ")"
                usesVessel = true;
            }

            //
            String hqlAsString = hql.toString().trim();
            if (hqlAsString.endsWith("AND")) {
                hqlAsString = hqlAsString.substring(0, hqlAsString.lastIndexOf("AND"));
            }

            //
            Query query = session.createQuery(hqlAsString);
            if (usesArea) {
                query.setParameter("north", north);
                query.setParameter("east", east);
                query.setParameter("south", south);
                query.setParameter("west", west);
            }
            if (usesFrom) {
                query.setParameter("from", from);
            }
            if (usesTo) {
                query.setParameter("to", to);
            }
            if (usesType) {
                String className = "dk.dma.ais.abnormal.event.db.domain." + type;
                try {
                    Class clazz = Class.forName(className);
                    query.setParameter("classes", clazz);
                } catch (ClassNotFoundException e) {
                    throw new IllegalArgumentException("Class " + className + " not found.");
                }
            }
            if (usesVessel) {
                query.setParameter("vessel", vessel);
            }
            events = query.list();
        } finally {
            session.close();
        }

        return events;
    }

    @Override
    public List<Event> findEventsByFromAndTo(Date from, Date to) {
        Session session = getSession();

        List events = null;
        try {
            StringBuilder hql = new StringBuilder();
            hql.append("SELECT e FROM Event e WHERE ");
            hql.append("(e.startTime >= :from AND e.startTime <= :to) OR ");
            hql.append("(e.endTime >= :from AND e.endTime <= :to)");

            //
            Query query = session.createQuery(hql.toString());
            query.setParameter("from", from);
            query.setParameter("to", to);
            events = query.list();
        } finally {
            session.close();
        }

        return events;
    }

    @Override
    public List<Event> findRecentEvents(int numberOfEvents) {
        Session session = getSession();

        List events = null;
        try {
            Query query = session.createQuery("SELECT e FROM Event e ORDER BY e.startTime DESC");
            query.setMaxResults(numberOfEvents);
            events = query.list();
        } finally {
            session.close();
        }

        return events;
    }

    @Override
    public <T extends Event> T findOngoingEventByVessel(int mmsi, Class<T> eventClass) {
        Session session = getSession();

        T event = null;
        try {
            Query query = session.createQuery("SELECT e FROM Event e WHERE TYPE(e)=:class AND e.state=:state AND e.behaviour.vessel.mmsi=:mmsi");
            query.setParameter("class", eventClass);
            query.setString("state", "ONGOING");
            query.setInteger("mmsi", mmsi);
            List events = query.list();

            if (events.size() > 0) {
                if (events.size() > 1) {
                    LOG.warn("More than one (" + events.size() + ") ongoing event of type " + eventClass + "; expected max. 1. Using first.");
                }
                event = (T) events.get(0);
            }

        } finally {
            session.close();
        }

        return event;
    }
}
