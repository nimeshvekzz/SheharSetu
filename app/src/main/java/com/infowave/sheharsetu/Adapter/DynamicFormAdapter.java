package com.infowave.sheharsetu.Adapter;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.infowave.sheharsetu.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Adapter for dynamic form:
 * Field map keys:
 *  - key, label, hint, type (TEXT, NUMBER, PHONE, EMAIL, DATE, DROPDOWN, CHECKBOX, SWITCH, TEXTAREA, CURRENCY, LOCATION, PHOTOS)
 *  - required (Boolean/Number/String)
 *  - options:
 *      - static mode: List<String>
 *      - backend mode: List<Map<String,Object>> with "value" and "label"
 */
public class DynamicFormAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "DynamicFormAdapter";

    /* ================= Callbacks to Activity ================= */
    public interface Callbacks {
        void pickCoverPhoto(String fieldKey);
        void pickMorePhotos(String fieldKey);
        void requestMyLocation(String fieldKey);
        void showToast(String msg);
    }

    /* ================= View Types ================= */
    private static final int T_TEXT      = 1;
    private static final int T_DATE      = 2;
    private static final int T_DROPDOWN  = 3;
    private static final int T_CHECKBOX  = 4;
    private static final int T_SWITCH    = 5;
    private static final int T_TEXTAREA  = 6;
    private static final int T_CURRENCY  = 7;
    private static final int T_LOCATION  = 8;
    private static final int T_PHOTOS    = 9;

    private final List<Map<String, Object>> fields;
    private final Map<String, Object> answers = new HashMap<>();
    private final SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
    private final Callbacks callbacks;

    public DynamicFormAdapter(List<Map<String, Object>> fields, Callbacks callbacks) {
        this.fields = fields != null ? fields : new ArrayList<>();
        this.callbacks = callbacks;

        Log.d(TAG, "Adapter init with fields size=" + this.fields.size());
        int idx = 0;
        for (Map<String, Object> f : this.fields) {
            String key  = s(f.get("key"));
            String type = s(f.get("type"));
            boolean required = req(f);
            Object opts = f.get("options");
            int optsCount = 0;
            if (opts instanceof List) optsCount = ((List<?>) opts).size();
            Log.d(TAG, "Field[" + idx + "] key=" + key +
                    " type=" + type +
                    " required=" + required +
                    " optionsCount=" + optsCount);
            idx++;
        }

        // initialize answers
        for (Map<String, Object> f : this.fields) {
            String key = s(f.get("key"));
            String type = s(f.get("type")).toUpperCase(Locale.ROOT);
            switch (type) {
                case "CHECKBOX":
                    // default OFF
                    answers.put(key, false);
                    break;
                case "SWITCH": {
                    // ✅ All switches default OFF (false).
                    //    "is_new" bhi yahi se OFF se start karega (Used by default).
                    boolean defaultVal = false;
                    answers.put(key, defaultVal);
                    break;
                }
                case "PHOTOS": {
                    Map<String, Object> ph = new HashMap<>();
                    ph.put("cover", "");                       // Base64 string
                    ph.put("more", new ArrayList<String>());   // List<Base64>
                    answers.put(key, ph);
                    break;
                }
                default:
                    answers.put(key, "");
            }
        }
        Log.d(TAG, "Answers initialized for all fields");
    }

    @Override
    public int getItemViewType(int position) {
        String t = s(fields.get(position).get("type")).toUpperCase(Locale.ROOT);
        switch (t) {
            case "DATE":     return T_DATE;
            case "DROPDOWN": return T_DROPDOWN;
            case "CHECKBOX": return T_CHECKBOX;
            case "SWITCH":   return T_SWITCH;
            case "TEXTAREA": return T_TEXTAREA;
            case "CURRENCY": return T_CURRENCY;
            case "LOCATION": return T_LOCATION;
            case "PHOTOS":   return T_PHOTOS;
            default:         return T_TEXT;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (vt == T_DATE)        return new VHDate(inf.inflate(R.layout.item_form_date, parent, false));
        if (vt == T_DROPDOWN)    return new VHDropdown(inf.inflate(R.layout.item_form_dropdown, parent, false));
        if (vt == T_CHECKBOX)    return new VHCheckbox(inf.inflate(R.layout.item_form_checkbox, parent, false));
        if (vt == T_SWITCH)      return new VHSwitch(inf.inflate(R.layout.item_form_switch, parent, false));
        if (vt == T_TEXTAREA)    return new VHTextArea(inf.inflate(R.layout.item_form_textarea, parent, false));
        if (vt == T_CURRENCY)    return new VHCurrencies(inf.inflate(R.layout.item_form_currency, parent, false));
        if (vt == T_LOCATION)    return new VHLocation(inf.inflate(R.layout.item_form_location, parent, false));
        if (vt == T_PHOTOS)      return new VHPhotos(inf.inflate(R.layout.item_form_photos, parent, false));
        return new VHText(inf.inflate(R.layout.item_form_text, parent, false));
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
        Map<String, Object> f = fields.get(pos);
        String key   = s(f.get("key"));
        String label = s(f.get("label"));
        String hint  = s(f.get("hint"));
        String type  = s(f.get("type"));

        Log.d(TAG, "onBindViewHolder pos=" + pos + " key=" + key + " type=" + type);

        if (h instanceof VHText) {
            VHText vh = (VHText) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            vh.etValue.setHint(hint);
            if ("NUMBER".equalsIgnoreCase(type)) {
                vh.etValue.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            } else if ("PHONE".equalsIgnoreCase(type)) {
                vh.etValue.setInputType(InputType.TYPE_CLASS_PHONE);
            } else if ("EMAIL".equalsIgnoreCase(type)) {
                vh.etValue.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            } else {
                vh.etValue.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            }
            vh.etValue.setText(s(answers.get(key)));
            vh.etValue.addTextChangedListener(new SimpleTextWatcher(s -> answers.put(key, s)));

        } else if (h instanceof VHTextArea) {
            VHTextArea vh = (VHTextArea) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            vh.etValue.setHint(hint);
            vh.etValue.setText(s(answers.get(key)));
            vh.etValue.addTextChangedListener(new SimpleTextWatcher(s -> answers.put(key, s)));

        } else if (h instanceof VHCurrencies) {
            VHCurrencies vh = (VHCurrencies) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            vh.etValue.setHint(hint);
            vh.etValue.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            vh.etValue.setText(s(answers.get(key)));
            vh.etValue.addTextChangedListener(new SimpleTextWatcher(s -> answers.put(key, s)));

        } else if (h instanceof VHDate) {
            VHDate vh = (VHDate) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            vh.etDate.setHint(hint);
            vh.etDate.setOnClickListener(v -> {
                Calendar c = Calendar.getInstance();
                DatePickerDialog dlg = new DatePickerDialog(v.getContext(),
                        (view, year, month, day) -> {
                            Calendar chosen = Calendar.getInstance();
                            chosen.set(year, month, day, 0, 0, 0);
                            String val = df.format(chosen.getTime());
                            vh.etDate.setText(val);
                            answers.put(key, val);
                        },
                        c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
                dlg.show();
            });
            vh.etDate.setText(s(answers.get(key)));

        } else if (h instanceof VHDropdown) {
            VHDropdown vh = (VHDropdown) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));

            final Context ctx = vh.itemView.getContext();

            Object optObj = f.get("options");
            List<String> displayList = new ArrayList<>();
            List<String> valueList   = new ArrayList<>();

            displayList.add("Select...");
            valueList.add("");

            if (optObj instanceof List) {
                List<?> rawList = (List<?>) optObj;
                for (Object o : rawList) {
                    if (o instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<Object, Object> mo = (Map<Object, Object>) o;
                        String val = s(mo.get("value"));
                        String lab = s(mo.get("label"));
                        if (TextUtils.isEmpty(lab)) lab = val;
                        displayList.add(lab);
                        valueList.add(val);
                    } else {
                        String s = String.valueOf(o);
                        displayList.add(s);
                        valueList.add(s);
                    }
                }
            }
            Log.d(TAG, "Dropdown key=" + key + " options displayCount=" + displayList.size() +
                    " valueCount=" + valueList.size());

            final List<String> finalDisplay = displayList;
            final List<String> finalValues  = valueList;

            ArrayAdapter<String> ad = new ArrayAdapter<String>(ctx, R.layout.spinner_item, finalDisplay) {
                @Override public boolean isEnabled(int position) {
                    return position != 0;
                }
                @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                    View v = super.getDropDownView(position, convertView, parent);
                    TextView tv = (TextView) v;
                    int color = (position == 0)
                            ? ContextCompat.getColor(ctx, R.color.ss_hint)
                            : ContextCompat.getColor(ctx, R.color.ss_on_surface);
                    tv.setTextColor(color);
                    return v;
                }
                @Override public View getView(int position, View convertView, ViewGroup parent) {
                    View v = super.getView(position, convertView, parent);
                    TextView tv = v.findViewById(R.id.spinnerText);
                    if (tv == null && v instanceof TextView) tv = (TextView) v;
                    if (tv != null) {
                        int color = (position == 0)
                                ? ContextCompat.getColor(ctx, R.color.ss_hint)
                                : ContextCompat.getColor(ctx, R.color.ss_on_surface);
                        tv.setTextColor(color);
                    }
                    return v;
                }
            };
            ad.setDropDownViewResource(R.layout.spinner_dropdown_item);
            vh.spinner.setAdapter(ad);

            String saved = s(answers.get(key));
            int idxSaved = finalValues.indexOf(saved);
            if (idxSaved < 0) idxSaved = 0;
            vh.spinner.setSelection(idxSaved);
            Log.d(TAG, "Dropdown key=" + key + " savedValue=" + saved + " index=" + idxSaved);

            vh.spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position == 0) {
                        answers.put(key, "");
                        Log.d(TAG, "Dropdown key=" + key + " set to empty (position 0)");
                    } else {
                        answers.put(key, finalValues.get(position));
                        Log.d(TAG, "Dropdown key=" + key + " set to value=" + finalValues.get(position));
                    }

                    if (view instanceof TextView) {
                        ((TextView) view).setTextColor(
                                ContextCompat.getColor(ctx, position == 0 ? R.color.ss_hint : R.color.ss_on_surface)
                        );
                    } else if (view != null) {
                        TextView tv = view.findViewById(R.id.spinnerText);
                        if (tv != null) {
                            tv.setTextColor(
                                    ContextCompat.getColor(ctx, position == 0 ? R.color.ss_hint : R.color.ss_on_surface)
                            );
                        }
                    }
                }
                @Override public void onNothingSelected(AdapterView<?> parent) { }
            });

        } else if (h instanceof VHCheckbox) {
            VHCheckbox vh = (VHCheckbox) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            vh.cb.setText(hint);
            boolean checked = answers.get(key) instanceof Boolean && (Boolean) answers.get(key);
            vh.cb.setChecked(checked);
            vh.cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                answers.put(key, isChecked);
                Log.d(TAG, "Checkbox key=" + key + " changed to " + isChecked);
            });

        } else if (h instanceof VHSwitch) {
            VHSwitch vh = (VHSwitch) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            boolean on = answers.get(key) instanceof Boolean && (Boolean) answers.get(key);
            vh.sw.setChecked(on);
            vh.sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
                answers.put(key, isChecked);
                Log.d(TAG, "Switch key=" + key + " changed to " + isChecked);
            });

        } else if (h instanceof VHLocation) {
            VHLocation vh = (VHLocation) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            vh.etLocation.setHint(hint);
            vh.etLocation.setText(s(answers.get(key)));
            vh.etLocation.addTextChangedListener(new SimpleTextWatcher(s -> {
                answers.put(key, s);
                Log.d(TAG, "Location key=" + key + " text changed to: " + s);
            }));
            vh.btnUseMyLocation.setOnClickListener(v -> {
                Log.d(TAG, "UseMyLocation clicked for key=" + key);
                if (callbacks != null) callbacks.requestMyLocation(key);
            });

        } else if (h instanceof VHPhotos) {
            VHPhotos vh = (VHPhotos) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            vh.tvHelper.setText(TextUtils.isEmpty(hint) ? "Clear, no blur" : hint);
            if (vh.tvTip != null) {
                vh.tvTip.setText("Tip: The first selected photo becomes the cover. Tap any thumbnail to change or remove.");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> ph = (Map<String, Object>) answers.get(key);
            String cover = ph == null ? "" : s(ph.get("cover")); // Base64
            @SuppressWarnings("unchecked")
            List<String> more = ph == null ? new ArrayList<>() : (List<String>) ph.get("more"); // Base64 list
            if (more == null) more = new ArrayList<>();

            Log.d(TAG, "Photos key=" + key + " coverBase64Empty=" + TextUtils.isEmpty(cover) + " moreCount=" + more.size());

            if (vh.rv.getLayoutManager() == null) {
                vh.rv.setLayoutManager(new LinearLayoutManager(vh.itemView.getContext(), RecyclerView.HORIZONTAL, false));
            }
            PhotosStripAdapter psa = new PhotosStripAdapter(
                    key,
                    cover,
                    more,
                    new PhotosStripAdapter.Events() {
                        @Override
                        public void onAddMore(String fieldKey) {
                            Log.d(TAG, "Photos onAddMore for key=" + fieldKey);
                            if (callbacks != null) callbacks.pickMorePhotos(fieldKey);
                        }

                        @Override
                        public void onSetCover(String fieldKey, int indexInList) {
                            Log.d(TAG, "Photos onSetCover key=" + fieldKey + " index=" + indexInList);
                            setCoverFromMore(fieldKey, indexInList);
                        }

                        @Override
                        public void onRemove(String fieldKey, int indexInMore, boolean isCover) {
                            Log.d(TAG, "Photos onRemove key=" + fieldKey + " indexInMore=" + indexInMore + " isCover=" + isCover);
                            removePhoto(fieldKey, indexInMore, isCover);
                        }
                    }
            );

            vh.rv.setAdapter(psa);

            String msg = (TextUtils.isEmpty(cover) ? "Cover: not selected" : "Cover: selected")
                    + "   |   More: " + more.size() + " selected";
            vh.tvPhotoStatus.setText(msg);
        }
    }

    @Override
    public int getItemCount() {
        int size = fields.size();
        Log.d(TAG, "getItemCount() = " + size);
        return size;
    }

    /* ================= Photos helpers ================= */

    @SuppressLint("NotifyDataSetChanged")
    private void setCoverFromMore(String fieldKey, int indexInMore) {
        @SuppressWarnings("unchecked")
        Map<String, Object> ph = (Map<String, Object>) answers.get(fieldKey);
        if (ph == null) return;
        String currentCover = s(ph.get("cover"));
        @SuppressWarnings("unchecked")
        List<String> more = (List<String>) ph.get("more");
        if (more == null || indexInMore < 0 || indexInMore >= more.size()) return;

        String newCover = more.get(indexInMore);
        more.set(indexInMore, currentCover);
        ph.put("cover", newCover);
        Log.d(TAG, "setCoverFromMore key=" + fieldKey +
                " newCover(set) length=" + (newCover == null ? 0 : newCover.length()) +
                " oldCoverLength=" + (currentCover == null ? 0 : currentCover.length()));
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void removePhoto(String fieldKey, int indexInMore, boolean isCover) {
        @SuppressWarnings("unchecked")
        Map<String, Object> ph = (Map<String, Object>) answers.get(fieldKey);
        if (ph == null) return;

        String cover = s(ph.get("cover"));
        @SuppressWarnings("unchecked")
        List<String> more = (List<String>) ph.get("more");
        if (more == null) {
            more = new ArrayList<>();
            ph.put("more", more);
        }

        boolean removed = false;

        if (isCover) {
            if (!TextUtils.isEmpty(cover)) {
                ph.put("cover", "");
                removed = true;
            }
        } else {
            if (indexInMore >= 0 && indexInMore < more.size()) {
                more.remove(indexInMore);
                removed = true;
            }
        }

        if (removed) {
            if (TextUtils.isEmpty(s(ph.get("cover"))) && more != null && !more.isEmpty()) {
                ph.put("cover", more.get(0));
                more.remove(0);
                toast("Cover removed. Promoted next image as cover.");
                Log.d(TAG, "removePhoto: cover removed, promoted next as cover");
            } else {
                toast("Photo removed.");
                Log.d(TAG, "removePhoto: removed non-cover photo");
            }
            notifyDataSetChanged();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setTextAnswer(String fieldKey, String value) {
        Log.d(TAG, "setTextAnswer key=" + fieldKey + " value=" + value);
        answers.put(fieldKey, value == null ? "" : value);
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setCoverPhoto(String fieldKey, String base64) {
        if (TextUtils.isEmpty(base64)) return;
        @SuppressWarnings("unchecked")
        Map<String, Object> ph = (Map<String, Object>) answers.get(fieldKey);
        if (ph == null) return;
        ph.put("cover", base64);
        Log.d(TAG, "setCoverPhoto key=" + fieldKey + " base64Length=" + base64.length());
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void addMorePhotos(String fieldKey, List<String> base64List) {
        if (base64List == null || base64List.isEmpty()) return;

        @SuppressWarnings("unchecked")
        Map<String, Object> ph = (Map<String, Object>) answers.get(fieldKey);
        if (ph == null) return;

        String cover = s(ph.get("cover"));
        @SuppressWarnings("unchecked")
        List<String> more = (List<String>) ph.get("more");
        if (more == null) {
            more = new ArrayList<>();
            ph.put("more", more);
        }

        boolean coverWasEmpty = TextUtils.isEmpty(cover);

        if (coverWasEmpty) {
            ph.put("cover", base64List.get(0));
            for (int i = 1; i < base64List.size(); i++) {
                String b64 = base64List.get(i);
                if (!TextUtils.isEmpty(b64)) more.add(b64);
            }
        } else {
            for (String b64 : base64List) {
                if (!TextUtils.isEmpty(b64)) more.add(b64);
            }
        }

        Log.d(TAG, "addMorePhotos key=" + fieldKey +
                " addedCount=" + base64List.size() +
                " coverWasEmpty=" + coverWasEmpty +
                " newCoverLength=" + s(ph.get("cover")).length() +
                " moreCount=" + more.size());

        notifyDataSetChanged();
        if (coverWasEmpty) {
            toast("First photo set as cover. Tap any thumbnail to change or remove.");
        } else {
            toast("Added " + base64List.size() + " photo(s). Tap a thumbnail to set cover or remove.");
        }
    }

    /* ================= Validation & JSON ================= */

    public JSONObject validateAndBuildResult() {
        try {
            Log.d(TAG, "validateAndBuildResult() started");
            for (Map<String, Object> f : fields) {
                String key = s(f.get("key"));
                String label = s(f.get("label"));
                String type = s(f.get("type"));
                boolean required = req(f);

                Object val = answers.get(key);
                String sval = val == null ? "" : String.valueOf(val);

                Log.d(TAG, "Validating key=" + key + " type=" + type +
                        " required=" + required + " value=" + sval);

                if (required) {
                    if ("CHECKBOX".equalsIgnoreCase(type) || "SWITCH".equalsIgnoreCase(type)) {
                        // ✅ Special case: "is_new" switch is never forced to ON.
                        //    Required ka matlab yahan sirf "field exist hai", jo hamare paas hamesha hai.
                        if (!"is_new".equalsIgnoreCase(key)) {
                            if (!(val instanceof Boolean) || !((Boolean) val)) {
                                toast("Please enable: " + label);
                                Log.e(TAG, "Validation failed: checkbox/switch not enabled for " + key);
                                return null;
                            }
                        }
                    } else if ("DROPDOWN".equalsIgnoreCase(type)) {
                        if (TextUtils.isEmpty(sval)) {
                            toast("Please select " + label);
                            Log.e(TAG, "Validation failed: dropdown empty for " + key);
                            return null;
                        }
                    } else if ("PHOTOS".equalsIgnoreCase(type)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> ph = (Map<String, Object>) val;
                        String cover = ph == null ? "" : s(ph.get("cover"));
                        if (TextUtils.isEmpty(cover)) {
                            toast("Please add a cover photo");
                            Log.e(TAG, "Validation failed: no cover photo for " + key);
                            return null;
                        }
                    } else {
                        if (TextUtils.isEmpty(sval)) {
                            toast(label + " is required");
                            Log.e(TAG, "Validation failed: empty text for " + key);
                            return null;
                        }
                    }
                }

                if ("EMAIL".equalsIgnoreCase(type) && !TextUtils.isEmpty(sval) &&
                        !android.util.Patterns.EMAIL_ADDRESS.matcher(sval).matches()) {
                    toast("Please enter a valid Email");
                    Log.e(TAG, "Validation failed: invalid email for key=" + key);
                    return null;
                }
            }

            JSONObject result = new JSONObject();
            for (Map.Entry<String, Object> e : answers.entrySet()) {
                String key = e.getKey();
                Object v = e.getValue();
                if (v instanceof Map) {
                    Map<?, ?> m = (Map<?, ?>) v;
                    JSONObject jo = new JSONObject();
                    for (Map.Entry<?, ?> me : m.entrySet()) {
                        if (me.getValue() instanceof List) {
                            JSONArray arr = new JSONArray();
                            for (Object o : (List<?>) me.getValue()) arr.put(String.valueOf(o));
                            jo.put(String.valueOf(me.getKey()), arr);
                        } else {
                            jo.put(String.valueOf(me.getKey()), String.valueOf(me.getValue()));
                        }
                    }
                    result.put(key, jo);
                } else {
                    result.put(key, v);
                }
            }
            Log.d(TAG, "validateAndBuildResult() final JSON: " + result.toString());
            return result;

        } catch (Exception e) {
            Log.e(TAG, "Error building JSON", e);
            toast("Error building JSON");
            return null;
        }
    }

    private void toast(String s) { if (callbacks != null) callbacks.showToast(s); }

    /* ================= Photos strip adapter ================= */

    static class PhotosStripAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        interface Events {
            void onAddMore(String fieldKey);
            void onSetCover(String fieldKey, int indexInList);
            void onRemove(String fieldKey, int indexInMore, boolean isCover);
        }

        private static final int V_THUMB = 1;
        private static final int V_ADD   = 2;

        private final String fieldKey;
        private final String coverBase64;        // base64 string (may be empty)
        private final List<String> moreBase64;   // base64 strings
        private final Events events;

        PhotosStripAdapter(String fieldKey, String cover, List<String> more, Events events) {
            this.fieldKey = fieldKey;
            this.coverBase64 = cover == null ? "" : cover;
            this.moreBase64  = more == null ? new ArrayList<>() : more;
            this.events = events;
        }

        @Override
        public int getItemCount() {
            return (TextUtils.isEmpty(coverBase64) ? 0 : 1) + moreBase64.size() + 1; // +1 for "add" item
        }

        @Override
        public int getItemViewType(int position) {
            int thumbs = (TextUtils.isEmpty(coverBase64) ? 0 : 1) + moreBase64.size();
            return position == thumbs ? V_ADD : V_THUMB;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (vt == V_ADD) {
                View v = inf.inflate(R.layout.item_photo_add, parent, false);
                return new VHAdd(v);
            } else {
                View v = inf.inflate(R.layout.item_photo_thumb, parent, false);
                return new VHT(v);
            }
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
            if (holder instanceof VHAdd) {
                holder.itemView.setOnClickListener(v -> {
                    if (events != null) events.onAddMore(fieldKey);
                });
                return;
            }

            String base64;
            boolean isCover;
            int idxInMore;

            if (!TextUtils.isEmpty(coverBase64)) {
                if (pos == 0) {
                    idxInMore = -1;
                    base64 = coverBase64;
                    isCover = true;
                } else {
                    idxInMore = pos - 1;
                    base64 = moreBase64.get(idxInMore);
                    isCover = false;
                }
            } else {
                idxInMore = pos;
                base64 = moreBase64.get(idxInMore);
                isCover = false;
            }

            VHT vh = (VHT) holder;

            // Decode Base64 -> Bitmap for preview
            try {
                if (!TextUtils.isEmpty(base64)) {
                    byte[] data = Base64.decode(base64, Base64.DEFAULT);
                    Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                    vh.iv.setImageBitmap(bmp);
                } else {
                    vh.iv.setImageDrawable(null);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to decode base64 image for thumbnails", e);
                vh.iv.setImageDrawable(null);
            }

            vh.badge.setVisibility(isCover ? View.VISIBLE : View.GONE);
            if (vh.container != null) {
                vh.container.setBackgroundResource(isCover ? R.drawable.bg_thumb_cover : R.drawable.bg_thumb_normal);
            }

            if (isCover) {
                vh.name.setText("Cover photo");
            } else {
                vh.name.setText("Photo " + (idxInMore + 1));
            }

            vh.remove.setVisibility(View.VISIBLE);
            vh.remove.bringToFront();
            vh.remove.setOnClickListener(v -> {
                if (events != null) events.onRemove(fieldKey, idxInMore, isCover);
            });

            if (!isCover) {
                vh.itemView.setOnClickListener(v -> {
                    if (events != null) events.onSetCover(fieldKey, idxInMore);
                });
                vh.itemView.setOnLongClickListener(v -> {
                    if (events != null) events.onSetCover(fieldKey, idxInMore);
                    return true;
                });
            } else {
                vh.itemView.setOnClickListener(null);
                vh.itemView.setOnLongClickListener(null);
            }
        }

        static class VHT extends RecyclerView.ViewHolder {
            View container; ImageView iv; TextView name; TextView badge; ImageView remove;
            VHT(@NonNull View v) {
                super(v);
                container = v.findViewById(R.id.thumbContainer);
                iv = v.findViewById(R.id.ivThumb);
                name = v.findViewById(R.id.tvName);
                badge = v.findViewById(R.id.tvBadge);
                remove = v.findViewById(R.id.ivRemove);
            }
        }
        static class VHAdd extends RecyclerView.ViewHolder {
            VHAdd(@NonNull View v) { super(v); }
        }

        // Kept as-is (no longer used, but retained)
        private static String fileName(Context ctx, Uri uri) {
            String result = null;
            if ("content".equals(uri.getScheme())) {
                try (Cursor cursor = ctx.getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (idx >= 0) result = cursor.getString(idx);
                    }
                } catch (Exception ignored) {}
            }
            if (result == null) {
                result = uri.getLastPathSegment();
                if (result == null) result = uri.toString();
            }
            return result;
        }
    }

    /* ================= ViewHolders ================= */

    static class VHText extends RecyclerView.ViewHolder {
        TextView tvLabel; EditText etValue;
        VHText(@NonNull View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tvLabel);
            etValue = v.findViewById(R.id.etValue);
        }
    }

    static class VHTextArea extends RecyclerView.ViewHolder {
        TextView tvLabel; EditText etValue;
        VHTextArea(@NonNull View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tvLabel);
            etValue = v.findViewById(R.id.etValue);
        }
    }

    static class VHCurrencies extends RecyclerView.ViewHolder {
        TextView tvLabel; EditText etValue;
        VHCurrencies(@NonNull View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tvLabel);
            etValue = v.findViewById(R.id.etValue);
        }
    }

    static class VHDate extends RecyclerView.ViewHolder {
        TextView tvLabel; EditText etDate;
        VHDate(@NonNull View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tvLabel);
            etDate = v.findViewById(R.id.etDate);
        }
    }

    static class VHDropdown extends RecyclerView.ViewHolder {
        TextView tvLabel; Spinner spinner;
        VHDropdown(@NonNull View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tvLabel);
            spinner = v.findViewById(R.id.spinner);
        }
    }

    static class VHCheckbox extends RecyclerView.ViewHolder {
        TextView tvLabel; CheckBox cb;
        VHCheckbox(@NonNull View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tvLabel);
            cb = v.findViewById(R.id.cb);
        }
    }

    static class VHSwitch extends RecyclerView.ViewHolder {
        TextView tvLabel; @SuppressLint("UseSwitchCompatOrMaterialCode")
        Switch sw;
        VHSwitch(@NonNull View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tvLabel);
            sw = v.findViewById(R.id.sw);
        }
    }

    static class VHLocation extends RecyclerView.ViewHolder {
        TextView tvLabel; EditText etLocation; Button btnUseMyLocation;
        VHLocation(@NonNull View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tvLabel);
            etLocation = v.findViewById(R.id.etLocation);
            btnUseMyLocation = v.findViewById(R.id.btnUseMyLocation);
        }
    }

    static class VHPhotos extends RecyclerView.ViewHolder {
        TextView tvLabel, tvHelper, tvTip, tvPhotoStatus; RecyclerView rv;
        VHPhotos(@NonNull View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tvLabel);
            tvHelper = v.findViewById(R.id.tvHelper);
            tvTip = v.findViewById(R.id.tvTip);
            tvPhotoStatus = v.findViewById(R.id.tvPhotoStatus);
            rv = v.findViewById(R.id.rvPhotosStrip);
        }
    }

    /* ================= Utils ================= */
    private static String s(Object o) { return o == null ? "" : String.valueOf(o); }

    private static boolean req(Map<String, Object> f) {
        Object r = f.get("required");
        if (r instanceof Boolean) {
            return (Boolean) r;
        }
        if (r instanceof Number) {
            return ((Number) r).intValue() != 0;
        }
        if (r instanceof String) {
            String s = ((String) r).trim().toLowerCase(Locale.ROOT);
            return s.equals("1") || s.equals("true") || s.equals("yes");
        }
        return false;
    }

    public interface OnText { void on(String s); }
    static class SimpleTextWatcher implements android.text.TextWatcher {
        private final OnText cb;
        SimpleTextWatcher(OnText cb){this.cb=cb;}
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (cb!=null) cb.on(s.toString());
        }
        @Override public void afterTextChanged(android.text.Editable s) {}
    }
}
