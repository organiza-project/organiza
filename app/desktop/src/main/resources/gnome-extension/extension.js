// Reports the focused app's desktop file ID to the Organiza desktop app (ADR-0024).
// It answers Current and emits Changed on GNOME Shell's own session bus connection, with the app ID
// and the focused window's process, which a pause freezes. Window titles are never read. GNOME disables extensions on the lock screen, and disable()
// then reports that nothing is in front, so locked time is not counted.
import Gio from 'gi://Gio';
import GLib from 'gi://GLib';
import Shell from 'gi://Shell';
import {Extension} from 'resource:///org/gnome/shell/extensions/extension.js';

const INTERFACE = `
<node>
  <interface name="org.organiza.Focus">
    <method name="Current">
      <arg type="s" direction="out" name="app"/>
      <arg type="i" direction="out" name="pid"/>
    </method>
    <signal name="Changed">
      <arg type="s" name="app"/>
      <arg type="i" name="pid"/>
    </signal>
  </interface>
</node>`;

export default class OrganizaFocus extends Extension {
    enable() {
        this._tracker = Shell.WindowTracker.get_default();
        this._object = Gio.DBusExportedObject.wrapJSObject(INTERFACE, this);
        this._object.export(Gio.DBus.session, '/org/organiza/Focus');
        this._handler = this._tracker.connect('notify::focus-app', () => this._report(...this.Current()));
        this._report(...this.Current());
    }

    disable() {
        this._tracker.disconnect(this._handler);
        this._report('', 0);
        this._object.unexport();
        this._object = null;
        this._tracker = null;
    }

    Current() {
        const app = this._tracker.focus_app?.get_id() ?? '';
        const pid = app ? global.display.focus_window?.get_pid() ?? 0 : 0;
        return [app, pid];
    }

    _report(app, pid) {
        this._object.emit_signal('Changed', new GLib.Variant('(si)', [app, pid]));
    }
}
