/*
 * openwms.org, the Open Warehouse Management System.
 * Copyright (C) 2014 Heiko Scherrer
 *
 * This file is part of openwms.org.
 *
 * openwms.org is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as 
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * openwms.org is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software. If not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.openwms.tms.routing;

import javax.validation.constraints.NotNull;
import java.util.Optional;

import org.ameba.exception.NotFoundException;
import org.openwms.common.LocationGroupVO;
import org.openwms.common.LocationVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.hateoas.Link;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * A ActivitiMatrix.
 *
 * @author <a href="mailto:scherrer@openwms.org">Heiko Scherrer</a>
 */
@Component
class ActivitiMatrix implements Matrix {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActivitiMatrix.class);

    @Autowired
    private ActionRepository repository;
    @Autowired
    @Qualifier("simpleRestTemplate")
    private RestTemplate restTemplate;

    @Override
    public Action findBy(@NotNull String actionType, @NotNull Route route, LocationVO location, LocationGroupVO locationGroup) {
        // search explicitly...
        Optional<Action> prg = Optional.empty();
        if (null != location) {

            // First explicitly search for the Location and Route
            prg = repository.findByRouteAndLocationKey(route, location.getCoordinate());
            if (!prg.isPresent()) {

                // When Location is set but no Action exists, check by LocationGroup
                prg = findByLocationGroupByName(route, location.getLocationGroupName());
                if (!prg.isPresent()) {

                    // search the LocationGroup hierarchy the way up...
                    prg = findByLocationGroup(route, locationGroup);
                    if (!prg.isPresent()) {
                        String message = String.format("No Action found for Route [%s] on Location [%s] and LocationGroup [%s]", route.getRouteId(), location.getCoordinate(), location.getLocationGroupName());
                        LOGGER.info(message);
                        throw new NoRouteException(message);
                    }
                }
            }
        }

        // search for locgroup...
        if (!prg.isPresent()) {
            if (null == locationGroup) {
                String message = String.format("No Action found for Route [%s] and Location [%s] without LocationGroup", route.getRouteId(), location);
                LOGGER.info(message);
                throw new NoRouteException(message);
            }
            prg = findByLocationGroup(route, locationGroup);
        }
        return prg.orElseThrow(() -> {
            String message = String.format("No Action found for Route [%s], Location [%s], LocationGroup [%s]", route.getRouteId(), location, locationGroup);
            LOGGER.info(message);
            return new NoRouteException(message);
        });
    }

    private Optional<Action> findByLocationGroup(Route route, LocationGroupVO locationGroup) {
        Optional<Action> cp = repository.findByRouteAndLocationGroupName(route, locationGroup.getName());
        if (!cp.isPresent() && locationGroup.hasLink("_parent")) {
            cp = findByLocationGroup(route, findLocationGroup(locationGroup.getLink("_parent")));
        }
        return cp;
    }

    private LocationGroupVO findLocationGroup(Link parent) {
        LocationGroupVO lg = restTemplate.getForObject(parent.getHref(), LocationGroupVO.class);
        if (lg == null) {
            throw new NotFoundException(String.format("No LocationGroup found at [%s]", parent.getHref()));
        }
        return lg;
    }

    private Optional<Action> findByLocationGroupByName(Route route, String locationGroupName) {
        return repository.findByRouteAndLocationGroupName(route, locationGroupName);
    }
}
