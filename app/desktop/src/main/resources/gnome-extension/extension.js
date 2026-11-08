// Reports the focused app's desktop file ID to the Organiza desktop app (ADR-0024).
// It answers Current and emits Changed on GNOME Shell's own session bus connection. Only the ID is
// sent; window titles are never read. GNOME disables extensions on the lock screen, and disable()
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
    </method>
    <signal name="Changed">
      <arg type="s" name="app"/>
    </signal>
  </interface>
</node>`;

export default class OrganizaFocus extends Extension {
    enable() {
        this._tracker = Shell.WindowTracker.get_default();
        this._object = Gio.DBusExportedObject.wrapJSObject(INTERFACE, this);
        this._object.export(Gio.DBus.session, '/org/organiza/Focus');
        this._handler = this._tracker.connect('notify::focus-app', () => this._report(this.Current()));
        this._report(this.Current());
    }

    disable() {
        this._tracker.disconnect(this._handler);
        this._report('');
        this._object.unexport();
        this._object = null;
        this._tracker = null;
    }

    Current() {
        return this._tracker.focus_app?.get_id() ?? '';
    }

    _report(app) {
        this._object.emit_signal('Changed', new GLib.Variant('(s)', [app]));
    }
}
