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

package dk.dma.ais.abnormal.event.db.domain.builders;

import dk.dma.ais.abnormal.event.db.domain.ShipSizeOrTypeEvent;

/**
 * This builder follows the expression builder pattern
 * http://martinfowler.com/bliki/ExpressionBuilder.html
 */
public class AbnormalShipSizeOrTypeEventBuilder extends EventBuilder {

    ShipSizeOrTypeEvent event;

    public AbnormalShipSizeOrTypeEventBuilder() {
        event = new ShipSizeOrTypeEvent();
    }

    public static AbnormalShipSizeOrTypeEventBuilder AbnormalShipSizeOrTypeEvent() {
        return new AbnormalShipSizeOrTypeEventBuilder();
    }

    public AbnormalShipSizeOrTypeEventBuilder shipLength(int shipLength) {
        event.setShipLength(shipLength);
        return this;
    }

    public AbnormalShipSizeOrTypeEventBuilder shipType(int shipType) {
        event.setShipType(shipType);
        return this;
    }

    public ShipSizeOrTypeEvent getEvent() {
        return event;
    }

}
