/**
 *  Garage Door Minder
 *
 *  Copyright 2016 adzule loosely based on code by ObyCode
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Garage Door Minder",
    namespace: "adzule.garagedoorminder",
    author: "adzule",
    description: "Monitor a garage door and optionally close it when it's open too long",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/garage_contact.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/garage_contact@2x.png")


preferences {
    page(name: "preferencePage1")
}
def preferencePage1() {
    dynamicPage(name: "preferencePage1", title: "Preferences", install: true, uninstall: true) {
        section("Notify me when the door named...") {
            input "theSensor", "capability.garageDoorControl", multiple: false, required: true
        }
        section("Is left open for more than...") {
            input "maxOpenTime", "number", title: "Minutes?", required: true
        }
        section("Keep reminding until it's closed?") {
            input "keepReminding", "bool", title: "Keep Reminding?", required: true, submitOnChange: true
        }
        if(keepReminding)
        {
            section("Close after...") {
                input "closeAfter", "number", title: "# of warnings (0=never)", required: true, range: "0..*"
            }
        }
        else
        {
            section("Close after...") {
                input "closeAfter", "number", title: "# of warnings (0=never, max 1 when KeepReminding off)", required: true, range: "0..1"
            }
        }
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"

    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"

    unsubscribe()
    initialize()
}

def initialize() {
    subscribe(theSensor, "door", sensorTriggered)
}

def sensorTriggered(evt) {
    if (evt.value == "closed") {
        clearStatus()
    }
    else if (evt.value == "open" && state.status != "scheduled") {
        runIn(maxOpenTime * 60, takeAction, [overwrite: false])
        state.status = "scheduled"
        state.numWarnings = 0
    }
}

def takeAction(){
    if (state.status == "scheduled")
    {    
        state.numWarnings = state.numWarnings + 1
        log.debug "$theSensor was open too long, sending message ($state.numWarnings / $closeAfter warnings before attempting to close)"
        def msg = "Your $theSensor has been open for more than $maxOpenTime minutes!"
        sendPush msg
        if (closeAfter > 0 && state.numWarnings >= closeAfter)
        {
           log.debug "Attempting to close the door after $state.numWarnings tries..."
           theSensor.close()
        }
        else
        {
          log.debug "Skipping door close."
        }
        if (!keepReminding)
        {
          log.debug "Don't keep reminding.  Clear status."
          clearStatus()
        }
        else
        {
          log.debug "Keep reminding.  Reminding again in $maxOpenTime minutes."
          runIn(maxOpenTime * 60, takeAction, [overwrite: false])
        }
    } else {
        log.trace "Status is no longer scheduled. Not sending text."
    }
}

def clearStatus() {
    state.status = null
    state.numWarnings = 0
}