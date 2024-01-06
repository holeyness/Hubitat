/*

MIT License

Copyright (c) Ian Luo

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

metadata {
  definition(
    name: "Levoit Superior 6000S Humidifier",
    namespace: "NiklasGustafsson",
    author: "Ian Luo",
    description: "Supports controlling the Levoit Superior 6000S Humidifier",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    documentationLink: "https://github.com/dcmeglio/hubitat-bond/blob/master/README.md") {
    capability "Switch"
    capability "SwitchLevel"
    capability "RelativeHumidityMeasurement"
    capability "Actuator"
    capability "TemperatureMeasurement"

    attribute "target_humidity", "number";
    attribute "mode", "string";
    attribute "drying_mode", "boolean";
    attribute "display", "boolean";
    attribute "lacks_water", "boolean";
    attribute "water_tank_lifted", "boolean";
    attribute "filter_life_percentage", "number";

    command "setTargetHumidity", [
      [name: "TargetHumidity*", type: "NUMBER", description: "TargetHumidity (30 - 80)"]
    ]
    command "setDisplay", [
      [name: "Display*", type: "ENUM", description: "Display", constraints: ["on", "off"]]
    ]
    command "setMode", [
      [name: "Mode*", type: "ENUM", description: "Mode", constraints: ["humidity", "manual", "sleep", "auto"]]
    ]
    command "setDryingMode", [
      [name: "Mode*", type: "ENUM", description: "Drying Mode", constraints: ["on", "off"]]
    ]
    command "setMistLevel", [
      [name: "MistLevel", type: "NUMBER", description: "Mist level (1-9)"]
    ]

    command "toggle"
    command "update"
  }

  preferences {
    input("debugOutput", "bool", title: "Enable debug logging?", defaultValue: false, required: false)
  }
}

def installed() {
  logDebug "Installed with settings: ${settings}"
  updated();
}

def updated() {
  logDebug "Updated with settings: ${settings}"
  state.clear()
  unschedule()
  initialize()

  runIn(3, update)

  // Turn off debug log in 30 minutes
  if (settings?.debugOutput) runIn(1800, logDebugOff);
}

def uninstalled() {
  logDebug "Uninstalled app"
}

def initialize() {
  logDebug "initializing"
}

def on() {
  logDebug "on()"
  handlePower(true)
  handleEvent("switch", "on")

  if (state.mist_level != null) {
    setMistLevel(state.mist_level)
  } else {
    setMistLevel(1)
  }

  if (state.target_humidity != null) {
    setTargetHumidity(state.target_humidity)
  }

  if (state.mode != null) {
    setMode(state.mode)
  } else {
    update()
  }

  if (state.drying_mode != null) {
    setDryingMode(state.drying_mode)
  }

  if (state.display != null) {
    setDisplay(state.display ? "on" : "off")
  } else {
    setDisplay("on")
  }
}

def off() {
  logDebug "off()"
  handlePower(false)
  handleEvent("switch", "off")
}

def toggle() {
  logDebug "toggle()"
  if (device.currentValue("switch") == "on")
    off()
  else
    on()
}

def setLevel(value) {
  logDebug "setLevel ${value}"
  mist_level = convertRange(value, 0, 100, 1, 9, true)
  setMode("manual") // always manual if setLevel() cmd was called
  setMistLevel(mist_level)
}

def setMistLevel(mist_level) {
  logDebug "setMistLevel(${mist_level})"
  handleMistLevel(mist_level)
  state.mist_level = mist_level
  handleEvent("mist_level", mist_level)
  device.sendEvent(name: "mist_level", value: mist_level)
}

def setTargetHumidity(target_humidity) {
  logDebug "setTargetHumidity(${target_humidity})"
  setMode("auto")
  handleTargetHumidity(target_humidity)
  state.target_humidity = target_humidity
  handleEvent("target_humidity", target_humidity)
}

def setMode(mode) {
  logDebug "setMode(${mode})"
  handleMode(mode)
  state.mode = mode
  handleEvent("mode", mode)
}


def setDisplay(display_on) {
  logDebug "setDisplay(${display_on})"
  def enabled = display_on == "on"

  handleDisplay(enabled)
  state.display = enabled
  handleEvent("display", enabled)
}

def setDryingMode(drying_mode_on) {
  logDebug "setDryingMode(${drying_mode_on})"
  def enabled = drying_mode_on == "on"

  handleDryingMode(enabled)
  state.drying_mode = enabled
  handleEvent("drying_mode", enabled)
}

def logDebug(msg) {
  if (settings?.debugOutput) {
    log.debug msg
  }
}

def handlePower(on) {

  def result = false

  parent.sendBypassRequest(device, [
    data: [power_switch: on ? 1 : 0, switch_idx: 0],
    "method": "setSwitch",
    "source": "APP"
  ]) {
    resp ->
      if (checkHttpResponse("handleOn", resp)) {
        def operation = on ? "ON" : "OFF"
        logDebug "turned ${operation}()"
        result = true
      }
  }
  return result
}

def handleMistLevel(mist_level) {

  def result = false

  parent.sendBypassRequest(device, [
    data: [levelIdx: 0, levelType: "mist", virtualLevel: mist_level],
    "method": "setVirtualLevel",
    "source": "APP"
  ]) {
    resp ->
      if (checkHttpResponse("handleMistLevel", resp)) {
        logDebug "Set mist level"
        result = true
      }
  }
  return result
}

def handleMode(mode) {
  def result = false
  def serialized_mode = mode
  if (mode == "auto") {
    serialized_mode = "autoPro"
  }

  parent.sendBypassRequest(device, [
          data    : [work_mode: serialized_mode],
          "method": "setHumidityMode",
          "source": "APP"
  ]) {
    resp ->
      if (checkHttpResponse("handleMode", resp)) {
        logDebug "Set mode ${mode}"
        result = true
      }
  }
}

def handleDryingMode(drying_mode_enabled) {
  def result = false

  parent.sendBypassRequest(device, [
          data    : ["auto_drying_switch": drying_mode_enabled ? 1 : 0],
          "method": "setDryingMode",
          "source": "APP"
  ]) {
    resp ->
      if (checkHttpResponse("handleDryingMode", resp)) {
        logDebug "Set drying mode ${mode}"
        result = true
      }
  }
}

def handleDisplay(displayOn) {
  def result = false

  parent.sendBypassRequest(device, [
    data: ["screen_switch": displayOn ? 1 : 0],
    "method": "setDisplay",
    "source": "APP"
  ]) {
    resp ->
      if (checkHttpResponse("handleDisplay", resp)) {
        logDebug "Set display ${displayOn}"
        result = true
      }

    return result
  }
}

def handleTargetHumidity(target_humidity) {
  logDebug "handleTargetHumidity(${target_humidity})"
  def result = false

  parent.sendBypassRequest(device, [
    data: ["target_humidity": target_humidity],
    "method": "setTargetHumidity",
    "source": "APP"
  ]) {
    resp ->
      if (checkHttpResponse("handleTargetHumidity", resp)) {
        logDebug "Successfully set target humidity ${target_humidity}"
        result = true
      }
  }
  return result
}


def update() {

  logDebug "update()"

  def result = null

  parent.sendBypassRequest(device, [
    "method": "getHumidifierStatus",
    "source": "APP",
    "data": [: ]
  ]) {
    resp ->
      if (checkHttpResponse("update", resp)) {

        def response = resp.data.result.result

        logDebug "update: ${response}"

        handleEvent("switch", response.enabled ? "on" : "off")
        handleEvent("level", convertRange(response.mist_virtual_level, 1, 9, 0, 100, true))

        state.humidity = response.humidity
        handleEvent("humidity", response.humidity)

        state.temperature = response.temperature
        handleEvent("temperature", response.temperature)

        state.mist_level = response.mist_level
        handleEvent("mist_level", response.mist_level)

        state.target_humidity = response.configuration.auto_target_humidity
        handleEvent("target_humidity", response.configuration.auto_target_humidity)

        state.mode = response.mode
        handleEvent("mode", response.mode)

        state.drying_mode = response.mode
        handleEvent("drying_mode", response.mode)

        state.display = response.display
        handleEvent("display", response.display)

        state.lacks_water = response.water_lacks
        handleEvent("lacks_water", response.water_lacks)

        state.water_tank_lifted = response.water_tank_lifted
        handleEvent("water_tank_lifted", response.water_tank_lifted)
      }
  }
  return result
}


private void handleEvent(name, val) {
  logDebug "handleEvent(${name}, ${val})"
  device.sendEvent(name: name, value: val)
}

private BigDecimal convertRange(BigDecimal val, BigDecimal inMin, BigDecimal inMax, BigDecimal outMin, BigDecimal outMax, Boolean returnInt = true) {
  // Let make sure ranges are correct
  assert(inMin <= inMax);
  assert(outMin <= outMax);

  // Restrain input value
  if (val < inMin) val = inMin;
  else if (val > inMax) val = inMax;

  val = ((val - inMin) * (outMax - outMin)) / (inMax - inMin) + outMin;
  if (returnInt) {
    // If integer is required we use the Float round because the BigDecimal one is not supported/not working on Hubitat
    val = val.toFloat().round().toBigDecimal();
  }

  return (val);
}

def checkHttpResponse(action, resp) {
  if (resp.status == 200 || resp.status == 201 || resp.status == 204)
    return true
  else if (resp.status == 400 || resp.status == 401 || resp.status == 404 || resp.status == 409 || resp.status == 500) {
    log.error "${action}: ${resp.status} - ${resp.getData()}"
    return false
  } else {
    log.error "${action}: unexpected HTTP response: ${resp.status}"
    return false
  }
}
