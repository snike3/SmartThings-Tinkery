/**
 *  
 *  Smoke & Carbon Monoxide Detector using a Monoprice Dual Relay
 *
 *  Copyright 2015 SmartThings
 *  Copyright 2015 Justin Ellison
 *  Copyright 2016 Tim Polehna
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
 *	Device Type supporting all the features of the Monoprice device including both switches, with real-time status
 *  of both switch 1 and 2.
 *
 *  I hacked Justin Ellison's work with stuff from the Z-Wave Smoke Detector made by SmartThings to put this 
 *  together... Thanks!
 *	
 */
 

preferences {
  input ("delayMillis", "number", title: "Command delay in ms", 
    description: "Time in milliseconds to delay sending multiple commands.", defaultValue: 0,
    required: false, range: "0..5000")
}

metadata {
    definition (name: "Smoke & CO Detector Dual Relay (Monoprice)", namespace: "polehna", author: "Tim Polehna") {
        capability "Polling"
        capability "Refresh"
        capability "Smoke Detector"
        capability "Carbon Monoxide Detector"

		attribute "alarmState", "string"

        fingerprint deviceId: "0x1001", inClusters: "0x5E, 0x86, 0x72, 0x5A, 0x85, 0x59, 0x73, 0x25, 0x20, 0x27, 0x71, 0x2B, 0x2C, 0x75, 0x7A, 0x60, 0x32, 0x70"
    }

    simulator {
        //status "on": ""
        //status "off": ""
		status "smoke": "command: 2003, payload: FF"
		status "clear": "command: 2003, payload: 00"
		status "test": "command: 7105, payload: 0C FF"
		status "carbonMonoxide": "command: 2003, payload: FF"
		status "carbonMonoxide clear": "command: 2003, payload: 00"

        reply "2001FF,delay 100,2502": "command: 2503, payload: FF"
        reply "200100,delay 100,2502": "command: 2503, payload: 00"
    }

	tiles (scale: 2){
		multiAttributeTile(name:"smoke", type: "lighting", width: 6, height: 4){
			tileAttribute ("device.alarmState", key: "PRIMARY_CONTROL") {
				attributeState("clear", label:"clear", icon:"st.alarm.smoke.clear", backgroundColor:"#ffffff")
				attributeState("smoke", label:"SMOKE", icon:"st.alarm.smoke.smoke", backgroundColor:"#e86d13")
				attributeState("carbonMonoxide", label:"MONOXIDE", icon:"st.alarm.carbon-monoxide.carbon-monoxide", backgroundColor:"#e86d13")
				attributeState("tested", label:"TEST", icon:"st.alarm.smoke.test", backgroundColor:"#e86d13")
			}
		}
        standardTile("refresh", "device.alarmState", inactiveLabel: false, decoration: "flat") {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        main "smoke"
        details(["smoke","refresh"])
    }
}

def parse(String description) {
    log.debug "Parsing '${description}'"
    def result = []
    def cmd = zwave.parse(description)
    if (cmd) {
        result += zwaveEvent(cmd)
        log.debug "Parsed ${cmd} to ${result.inspect()}"
    } else {
        log.debug "Non-parsed event: ${description}"
    }
    return result
}

def createSmokeOrCOEvents(name) {
	def text = null
	if (name == "smoke") {
		text = "$device.displayName smoke was detected!"
		// these are displayed:false because the composite event is the one we want to see in the app
		createEvent(name: "smoke",          value: "detected", descriptionText: text, displayed: false)
	} else if (name == "carbonMonoxide") {
		text = "$device.displayName carbon monoxide was detected!"
		createEvent(name: "carbonMonoxide", value: "detected", descriptionText: text, displayed: false)
	} else if (name == "tested") {
		text = "$device.displayName was tested"
		createEvent(name: "smoke",          value: "tested", descriptionText: text, displayed: false)
		createEvent(name: "carbonMonoxide", value: "tested", descriptionText: text, displayed: false)
	} else if (name == "smokeClear") {
		text = "$device.displayName smoke is clear"
		createEvent(name: "smoke",          value: "clear", descriptionText: text, displayed: false)
		name = "clear"
	} else if (name == "carbonMonoxideClear") {
		text = "$device.displayName carbon monoxide is clear"
		createEvent(name: "carbonMonoxide", value: "clear", descriptionText: text, displayed: false)
		name = "clear"
	} else if (name == "testClear") {
		text = "$device.displayName smoke is clear"
		createEvent(name: "smoke",          value: "clear", descriptionText: text, displayed: false)
		createEvent(name: "carbonMonoxide", value: "clear", displayed: false)
		name = "clear"
	}
	// This composite event is used for updating the tile
	createEvent(name: "alarmState", value: name, descriptionText: text)
}

/*
def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
    def result
    if (cmd.value == 0) {
        result = createEvent(name: "switch", value: "off")
    } else {
        result = createEvent(name: "switch", value: "on")
    }
    return result
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    sendEvent(name: "switch", value: cmd.value ? "on" : "off", type: "digital")
    def result = []
    result << zwave.multiChannelV3.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:1, commandClass:37, command:2).format()
    result << zwave.multiChannelV3.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:2, commandClass:37, command:2).format()
    response(delayBetween(result, settings.delayMillis)) // returns the result of reponse()
}
*/

def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelCapabilityReport cmd) {
    log.debug "multichannelv3.MultiChannelCapabilityReport $cmd"
    if (cmd.endPoint == 2 ) {
        def currstate = device.currentState("switch2").getValue()
        if (currstate == "on")
        	createSmokeOrCOEvents("carbonMonoxide")
        else if (currstate == "off")
        	createSmokeOrCOEvents("carbonMonoxideClear")
    }
    else if (cmd.endPoint == 1 ) {
        def currstate = device.currentState("switch1").getValue()
        if (currstate == "on")
        	createSmokeOrCOEvents("smoke")
        else if (currstate == "off")
        	createSmokeOrCOEvents("smokeClear")
    }
}

def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
	def map = [ name: "switch$cmd.sourceEndPoint" ]

	if (cmd.commandClass == 37){
        if (cmd.parameter == [0]) {
            map.value = "off"
        }
        if (cmd.parameter == [255]) {
            map.value = "on"
        }
        createEvent(map)
    }
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    // This will capture any commands not handled by other instances of zwaveEvent
    // and is recommended for development so you can see every command the device sends
    return createEvent(descriptionText: "${device.displayName}: ${cmd}")
}

def refresh() {
    log.debug "Executing 'refresh'"
	def cmds = []
	cmds << zwave.multiChannelV3.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:1, commandClass:37, command:2).format()
    cmds << zwave.multiChannelV3.multiChannelCmdEncap(sourceEndPoint:1, destinationEndPoint:2, commandClass:37, command:2).format()
	delayBetween(cmds, settings.delayMillis)
}

def poll() {
    log.debug "Executing 'poll'"
	delayBetween([
    	zwave.switchBinaryV1.switchBinaryGet().format(),
    	zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
	], settings.delayMillis)
}
