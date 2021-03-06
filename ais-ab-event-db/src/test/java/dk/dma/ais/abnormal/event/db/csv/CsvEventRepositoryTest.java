package dk.dma.ais.abnormal.event.db.csv;

import dk.dma.ais.abnormal.event.db.domain.Event;
import dk.dma.ais.abnormal.event.db.domain.TrackingPoint;
import dk.dma.ais.abnormal.event.db.domain.builders.SuddenSpeedChangeEventBuilder;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;

public class CsvEventRepositoryTest {

    @Test
    public void save() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        CsvEventRepository sut = new CsvEventRepository(out, false);

        Event event =
            SuddenSpeedChangeEventBuilder.SuddenSpeedChangeEvent()
                .title("title")
                .description("description")
                .state(Event.State.PAST)
                .startTime(LocalDateTime.of(2017, 1, 22, 10, 0, 0))
                .endTime(LocalDateTime.of(2017, 1, 22, 12, 32, 1))
                .endTime(null)
                .behaviour()
                    .isPrimary(true)
                    .vessel()
                        .mmsi(123456789)
                        .imo(123456)
                        .callsign("callsign")
                        .type(1)
                        .toBow(10)
                        .toStern(20)
                        .toPort(30)
                        .toStarboard(40)
                        .name("name")
                    .trackingPoint()
                        .timestamp(LocalDateTime.of(2017, 1, 22, 10, 0, 0))
                        .positionInterpolated(false)
                        .eventCertainty(TrackingPoint.EventCertainty.RAISED)
                        .speedOverGround(12.0f)
                        .courseOverGround(45f)
                        .trueHeading(46f)
                        .latitude(56.0)
                        .longitude(12.0)
            .getEvent();

        sut.save(event);

        assertEquals(
            "eventId,eventType,startTime,endTime,title,description,mmsis,pMmsi,pName,pCallsign,pType,pLength,pLat,pLon,pSog,pCog,pHdg,sMmsi,sName,sCallsign,sType,sLength,sLat,sLon,sSog,sCog,sHdg,filterSuggest\r\n" +
            "0,SuddenSpeedChangeEvent,2017-01-22T10:00,,title,description,[123456789],123456789,name,callsign,1,30,56.0000,12.0000,12.0,45,46,,,,,,,,,,,\"-start \"\"2017-01-22-09:50\"\" -end \"\"2017-01-22-10:20\"\" -exp \"\"m.pos within circle(56.0000,12.0000,1000)\"\"\"\r\n",
            out.toString()
        );
    }

}