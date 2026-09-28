const report = document.getElementById('report');
const reportResult = document.getElementById('report-result');
const usbSection = document.getElementById('usb-section');
const manifestSection = document.getElementById('manifest-section');
const gateSection = document.getElementById('gate-section');
const usbResult = document.getElementById('usb-result');
const manifestInput = document.getElementById('manifest');
const manifestResult = document.getElementById('manifest-result');
const installer = document.getElementById('installer');
const webInstaller = document.getElementById('web-installer');
let manifestUrl = '';

function setResult(element, text, kind = '') {
  element.textContent = text;
  element.className = `result ${kind}`.trim();
}

document.getElementById('analyze').addEventListener('click', () => {
  const value = report.value.toLowerCase();
  usbSection.hidden = true;
  manifestSection.hidden = true;
  gateSection.hidden = true;
  installer.hidden = true;
  if (!value.includes('listener lab') || !value.includes('home assistant')) {
    setResult(reportResult, 'This does not look like a Listener Lab system report. Return to the Android app and use Copy Report.', 'bad');
    return;
  }
  const googleOnly = (value.includes('google') || value.includes('nest') || value.includes('cast')) &&
    !value.includes('esphome') && !value.includes('atom echo') && !value.includes('s3-box') && !value.includes('voice preview');
  const flashable = value.includes('desktop recovery path') &&
    (value.includes('esphome') || value.includes('atom echo') || value.includes('s3-box') || value.includes('voice preview'));
  if (googleOnly) {
    setResult(reportResult, 'Google/Nest audio was detected, but no supported flashable listener was identified. Do not connect or flash a Google Home device. Add or repair a dedicated Assist satellite instead.', 'warn');
    return;
  }
  if (!flashable) {
    setResult(reportResult, 'The Android report did not authorize a firmware path. Continue Home Assistant-side diagnosis; this tool will remain locked.', 'warn');
    return;
  }
  setResult(reportResult, 'Conditional firmware evidence found. USB inspection is available, but flashing remains locked until the exact manifest is verified.', 'good');
  usbSection.hidden = false;
});

document.getElementById('connect-usb').addEventListener('click', async () => {
  if (!('serial' in navigator)) {
    setResult(usbResult, 'Web Serial is unavailable. Use current Chrome or Edge on Windows, macOS, or Linux.', 'bad');
    return;
  }
  try {
    const port = await navigator.serial.requestPort();
    const info = port.getInfo();
    const vendor = info.usbVendorId ? `0x${info.usbVendorId.toString(16).padStart(4, '0')}` : 'not reported';
    const product = info.usbProductId ? `0x${info.usbProductId.toString(16).padStart(4, '0')}` : 'not reported';
    const bridges = { 0x303a:'Espressif native USB/JTAG/serial', 0x10c4:'Silicon Labs CP210x bridge', 0x1a86:'WCH CH340/CH341 bridge', 0x0403:'FTDI serial bridge' };
    const label = bridges[info.usbVendorId] || 'Unrecognized USB bridge';
    setResult(usbResult, `${label}\nVendor: ${vendor}\nProduct: ${product}\n\nA USB bridge is not proof of the exact board. Match the printed model and vendor documentation before continuing.`, 'good');
    manifestSection.hidden = false;
  } catch (error) {
    setResult(usbResult, error.name === 'NotFoundError' ? 'No USB device was selected.' : `USB inspection failed: ${error.message}`, 'bad');
  }
});

document.getElementById('inspect-manifest').addEventListener('click', async () => {
  manifestUrl = manifestInput.value.trim();
  gateSection.hidden = true;
  installer.hidden = true;
  if (!manifestUrl.startsWith('https://')) {
    setResult(manifestResult, 'The manifest must use HTTPS.', 'bad');
    return;
  }
  try {
    const response = await fetch(manifestUrl, { credentials:'omit', referrerPolicy:'no-referrer' });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const manifest = await response.json();
    if (!manifest.name || !manifest.version || !Array.isArray(manifest.builds) || manifest.builds.length === 0) {
      throw new Error('Missing required name, version, or builds fields');
    }
    const families = [...new Set(manifest.builds.map(build => build.chipFamily).filter(Boolean))];
    const invalidPart = manifest.builds.some(build => !Array.isArray(build.parts) || build.parts.some(part => !part.path || !Number.isInteger(part.offset)));
    if (invalidPart) throw new Error('One or more firmware parts are incomplete');
    setResult(manifestResult, `Name: ${manifest.name}\nVersion: ${manifest.version}\nChip families: ${families.join(', ') || 'not declared'}\nBuilds: ${manifest.builds.length}\n\nThe file is structurally valid. You must still verify that it belongs to the exact board.`, 'good');
    gateSection.hidden = false;
    updateGate();
  } catch (error) {
    setResult(manifestResult, `Manifest inspection failed: ${error.message}. The server must permit browser access with CORS.`, 'bad');
  }
});

['backup','trusted','matched','confirm'].forEach(id => document.getElementById(id).addEventListener('input', updateGate));

function updateGate() {
  const ready = document.getElementById('backup').checked &&
    document.getElementById('trusted').checked &&
    document.getElementById('matched').checked &&
    document.getElementById('confirm').value.trim() === 'FLASH' && manifestUrl.startsWith('https://');
  installer.hidden = !ready;
  if (ready) webInstaller.setAttribute('manifest', manifestUrl);
  else webInstaller.removeAttribute('manifest');
}

