import 'dart:html' as html;

class TokenPersist {
  static String? read(String key) => html.window.localStorage[key];

  static void write(String key, String value) {
    html.window.localStorage[key] = value;
  }

  static void delete(String key) {
    html.window.localStorage.remove(key);
  }
}
