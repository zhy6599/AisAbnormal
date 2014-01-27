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

package dk.dma.ais.abnormal.analyzer.analysis;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;
import dk.dma.ais.abnormal.analyzer.AppStatisticsService;
import dk.dma.ais.abnormal.event.db.EventRepository;
import dk.dma.ais.abnormal.event.db.domain.AbnormalCourseOverGroundEvent;
import dk.dma.ais.abnormal.event.db.domain.Event;
import dk.dma.ais.abnormal.event.db.domain.builders.TrackingPointBuilder;
import dk.dma.ais.abnormal.stat.db.FeatureDataRepository;
import dk.dma.ais.abnormal.stat.db.data.CourseOverGroundData;
import dk.dma.ais.abnormal.stat.db.data.FeatureData;
import dk.dma.ais.abnormal.stat.db.data.ShipTypeAndSizeData;
import dk.dma.ais.abnormal.tracker.Track;
import dk.dma.ais.abnormal.tracker.TrackingService;
import dk.dma.ais.abnormal.tracker.events.CellIdChangedEvent;
import dk.dma.ais.abnormal.tracker.events.TrackStaleEvent;
import dk.dma.ais.abnormal.util.Categorizer;
import dk.dma.enav.model.geometry.Position;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import static dk.dma.ais.abnormal.event.db.domain.builders.AbnormalCourseOverGroundEventBuilder.AbnormalCourseOverGroundEvent;

public class CourseOverGroundAnalysis extends StatisticalAnalysis {
    private static final Logger LOG = LoggerFactory.getLogger(CourseOverGroundAnalysis.class);
    {
        LOG.info(this.getClass().getSimpleName() + " created (" + this + ").");
    }

    private final AppStatisticsService statisticsService;
    private final EventRepository eventRepository;

    private static final int TOTAL_COUNT_THRESHOLD = 1000;

    @Inject
    public CourseOverGroundAnalysis(AppStatisticsService statisticsService, FeatureDataRepository featureDataRepository, TrackingService trackingService, EventRepository eventRepository) {
        super(featureDataRepository, trackingService);

        this.statisticsService = statisticsService;
        this.eventRepository = eventRepository;
    }

    @AllowConcurrentEvents
    @Subscribe
    public void onCellIdChanged(CellIdChangedEvent trackEvent) {
        statisticsService.incAnalysisStatistics(this.getClass().getSimpleName(), "Events received");

        Track track = trackEvent.getTrack();
        Long cellId = (Long) track.getProperty(Track.CELL_ID);
        Integer shipType = (Integer) track.getProperty(Track.SHIP_TYPE);
        Integer shipLength = (Integer) track.getProperty(Track.VESSEL_LENGTH);
        Float courseOverGround = (Float) track.getProperty(Track.COURSE_OVER_GROUND);

        if (cellId == null) {
            statisticsService.incAnalysisStatistics(this.getClass().getSimpleName(), "Unknown mmsi");
            return;
        }

        if (shipType == null) {
            statisticsService.incAnalysisStatistics(this.getClass().getSimpleName(), "Unknown ship type");
            return;
        }

        if (shipLength == null) {
            statisticsService.incAnalysisStatistics(this.getClass().getSimpleName(), "Unknown ship length");
            return;
        }

        if (courseOverGround == null) {
            statisticsService.incAnalysisStatistics(this.getClass().getSimpleName(), "Unknown course over ground");
            return;
        }

        short shipTypeBucket = Categorizer.mapShipTypeToCategory(shipType);
        short shipLengthBucket = Categorizer.mapShipLengthToCategory(shipLength);
        short courseOverGroundBucket = Categorizer.mapCourseOverGroundToCategory(courseOverGround);

        if (isAbnormalCourseOverGround(cellId, shipTypeBucket, shipLengthBucket, courseOverGroundBucket)) {
            raiseOrMaintainAbnormalEvent(track);
        } else {
            lowerExistingAbnormalEventIfExists(track);
        }

        statisticsService.incAnalysisStatistics(this.getClass().getSimpleName(), "Events processed");
    }

    @AllowConcurrentEvents
    @Subscribe
    public void onTrackStale(TrackStaleEvent trackEvent) {
        lowerExistingAbnormalEventIfExists(trackEvent.getTrack());
    }

    private void lowerExistingAbnormalEventIfExists(Track track) {
        Integer mmsi = track.getMmsi();
        Event ongoingEvent = eventRepository.findOngoingEventByVessel(mmsi, AbnormalCourseOverGroundEvent.class);
        if (ongoingEvent != null) {
            Date timestamp = new Date((Long) track.getProperty(Track.TIMESTAMP_ANY_UPDATE));
            ongoingEvent.setState(Event.State.PAST);
            ongoingEvent.setEndTime(timestamp);
            eventRepository.save(ongoingEvent);
        }
    }

    private void raiseOrMaintainAbnormalEvent(Track track) {
        Date positionTimestamp = new Date((Long) track.getProperty(Track.TIMESTAMP_POSITION_UPDATE));
        Integer mmsi = track.getMmsi();
        Integer imo = (Integer) track.getProperty(Track.IMO);
        String callsign = (String) track.getProperty(Track.CALLSIGN);
        String name = (String) track.getProperty(Track.SHIP_NAME);
        Position position = (Position) track.getProperty(Track.POSITION);
        Float cog = (Float) track.getProperty(Track.COURSE_OVER_GROUND);
        Float sog = (Float) track.getProperty(Track.SPEED_OVER_GROUND);
        Boolean interpolated = (Boolean) track.getProperty(Track.POSITION_IS_INTERPOLATED);

        Event ongoingEvent = eventRepository.findOngoingEventByVessel(mmsi, AbnormalCourseOverGroundEvent.class);

        if (ongoingEvent != null) {
            ongoingEvent.getBehaviour().addTrackingPoint(
                    TrackingPointBuilder.TrackingPoint()
                            .timestamp(positionTimestamp)
                            .positionInterpolated(interpolated)
                            .speedOverGround(sog)
                            .courseOverGround(cog)
                            .latitude(position.getLatitude())
                            .longitude(position.getLongitude())
                    .getTrackingPoint()
            );

            eventRepository.save(ongoingEvent);
        } else {
            Integer shipType = (Integer) track.getProperty(Track.SHIP_TYPE);
            Integer shipLength = (Integer) track.getProperty(Track.VESSEL_LENGTH);

            short shipTypeBucket = Categorizer.mapShipTypeToCategory(shipType);
            short shipLengthBucket = Categorizer.mapShipLengthToCategory(shipLength);
            short courseOverGroundBucket = Categorizer.mapCourseOverGroundToCategory(cog);

            Event event =
                    AbnormalCourseOverGroundEvent()
                            .shipType(shipTypeBucket)
                            .shipLength(shipLengthBucket)
                            .courseOverGround(courseOverGroundBucket)
                            .description("Ship type: " + shipType + ", ship length: " + shipLength + ", cog: " + cog)
                            .startTime(positionTimestamp)
                            .behaviour()
                                .vessel()
                                    .mmsi(mmsi)
                                    .imo(imo)
                                    .callsign(callsign)
                                    .name(name)
                                .trackingPoint()
                                    .timestamp(positionTimestamp)
                                    .positionInterpolated(interpolated)
                                    .speedOverGround(sog)
                                    .courseOverGround(cog)
                                    .latitude(position.getLatitude())
                                    .longitude(position.getLongitude())
                            .getEvent();

            eventRepository.save(event);
        }
    }

    /**
     * If the probability p(d)<0.001 and total count>1000 then abnormal. p(d)=sum(count)/count for all sog_intervals for
     * that shiptype and size.
     *
     * @param cellId
     * @param shipTypeBucket
     * @param shipSizeBucket
     * @return true if the presence of size/type in this cell is abnormal. False otherwise.
     */
    boolean isAbnormalCourseOverGround(Long cellId, int shipTypeBucket, int shipSizeBucket, int courseOverGroundBucket) {
        float pd = 1.0f;

        FeatureData courseOverGroundFeatureData = getFeatureDataRepository().getFeatureData("ShipTypeAndSizeFeature", cellId);

        if (courseOverGroundFeatureData instanceof CourseOverGroundData) {
            Integer totalCount  = ((CourseOverGroundData) courseOverGroundFeatureData).getSumFor("shipCount");
            if (totalCount > TOTAL_COUNT_THRESHOLD) {
                Integer shipCount = ((CourseOverGroundData) courseOverGroundFeatureData).getValue(shipTypeBucket, shipSizeBucket, courseOverGroundBucket, ShipTypeAndSizeData.STAT_SHIP_COUNT);
                if (shipCount == null) {
                    shipCount = 0;
                }
                pd = (float) shipCount / (float) totalCount;
                LOG.debug("cellId=" + cellId + ", shipType=" + shipTypeBucket + ", shipSize=" + shipSizeBucket + "cog=" + courseOverGroundBucket + ", shipCount=" + shipCount + ", totalCount=" + totalCount + ", pd=" + pd);
            } else {
                LOG.debug("totalCount of " + totalCount + " is not enough statistical data for cell " + cellId);
            }
        }

        LOG.debug("pd = " + pd);

        boolean isAbnormalCourseOverGround = pd < 0.001;
        if (isAbnormalCourseOverGround) {
            LOG.debug("Abnormal event detected.");
        } else {
            LOG.debug("Normal or inconclusive event detected.");
        }

        statisticsService.incAnalysisStatistics(this.getClass().getSimpleName(), "Analyses performed");

        return isAbnormalCourseOverGround;
    }
}
