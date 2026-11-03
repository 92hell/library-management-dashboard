import admin from './admin';
import common from './common';
import customer from './customer';
import desk from './desk';

/** Indonesian translations, keyed by the English text. Each area of the app has its own file. */
const parts = [common, customer, desk, admin];

const ID = {};
for (const part of parts) {
  for (const [key, value] of Object.entries(part)) {
    if (key in ID && ID[key] !== value) throw new Error(`Conflicting Indonesian translations for "${key}"`);
    ID[key] = value;
  }
}
export default ID;
