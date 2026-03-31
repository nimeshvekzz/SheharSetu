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
    private static final int T_TEXT = 1;
    private static final int T_DATE = 2;
    private static final int T_DROPDOWN = 3;
    private static final int T_CHECKBOX = 4;
    private static final int T_SWITCH = 5;
    private static final int T_TEXTAREA = 6;
    private static final int T_CURRENCY = 7;
    private static final int T_LOCATION = 8;
    private static final int T_PHOTOS = 9;

    private final List<Map<String, Object>> fields;
    private final Map<String, Object> answers = new HashMap<>();
    private final SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
    private final Callbacks callbacks;
    private final Context adapterContext;
    private boolean isBinding = false; // Suppress listener callbacks during bind


    public DynamicFormAdapter(List<Map<String, Object>> fields, Callbacks callbacks) {
        this.fields = fields != null ? fields : new ArrayList<>();
        this.callbacks = callbacks;
        this.adapterContext = callbacks instanceof Context ? (Context) callbacks : null;
        int idx = 0;
        for (Map<String, Object> f : this.fields) {
            String key = s(f.get("key"));
            String type = s(f.get("type"));
            boolean required = req(f);
            Object opts = f.get("options");
            int optsCount = 0;
            if (opts instanceof List)
                optsCount = ((List<?>) opts).size();
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
                    // "is_new" bhi yahi se OFF se start karega (Used by default).
                    boolean defaultVal = false;
                    answers.put(key, defaultVal);
                    break;
                }
                case "PHOTOS": {
                    Map<String, Object> ph = new HashMap<>();
                    ph.put("cover", ""); // Base64 string
                    ph.put("more", new ArrayList<String>()); // List<Base64>
                    answers.put(key, ph);
                    break;
                }
                default:
                    answers.put(key, "");
            }
        }
    }

    private String tr(String text) {
        if (adapterContext == null) return text == null ? "" : text;
        return I18n.t(adapterContext, text == null ? "" : text);
    }

    @Override
    public int getItemViewType(int position) {
        String t = s(fields.get(position).get("type")).toUpperCase(Locale.ROOT);
        switch (t) {
            case "DATE":
                return T_DATE;
            case "DROPDOWN":
                return T_DROPDOWN;
            case "CHECKBOX":
                return T_CHECKBOX;
            case "SWITCH":
                return T_SWITCH;
            case "TEXTAREA":
                return T_TEXTAREA;
            case "CURRENCY":
                return T_CURRENCY;
            case "LOCATION":
                return T_LOCATION;
            case "PHOTOS":
                return T_PHOTOS;
            default:
                return T_TEXT;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (vt == T_DATE)
            return new VHDate(inf.inflate(R.layout.item_form_date, parent, false));
        if (vt == T_DROPDOWN)
            return new VHDropdown(inf.inflate(R.layout.item_form_dropdown, parent, false));
        if (vt == T_CHECKBOX)
            return new VHCheckbox(inf.inflate(R.layout.item_form_checkbox, parent, false));
        if (vt == T_SWITCH)
            return new VHSwitch(inf.inflate(R.layout.item_form_switch, parent, false));
        if (vt == T_TEXTAREA)
            return new VHTextArea(inf.inflate(R.layout.item_form_textarea, parent, false));
        if (vt == T_CURRENCY)
            return new VHCurrencies(inf.inflate(R.layout.item_form_currency, parent, false));
        if (vt == T_LOCATION)
            return new VHLocation(inf.inflate(R.layout.item_form_location, parent, false));
        if (vt == T_PHOTOS)
            return new VHPhotos(inf.inflate(R.layout.item_form_photos, parent, false));
        return new VHText(inf.inflate(R.layout.item_form_text, parent, false));
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
        Map<String, Object> f = fields.get(pos);
        String key = s(f.get("key"));
        String label = tr(s(f.get("label")));
        String hint = tr(s(f.get("hint")));
        String type = s(f.get("type"));
        isBinding = true;
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
            vh.etValue.addTextChangedListener(new SimpleTextWatcher(s -> {
                if (!isBinding)
                    answers.put(key, s);
            }));

        } else if (h instanceof VHTextArea) {
            VHTextArea vh = (VHTextArea) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            vh.etValue.setHint(hint);
            vh.etValue.setText(s(answers.get(key)));
            vh.etValue.addTextChangedListener(new SimpleTextWatcher(s -> {
                if (!isBinding)
                    answers.put(key, s);
            }));

        } else if (h instanceof VHCurrencies) {
            VHCurrencies vh = (VHCurrencies) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            vh.etValue.setHint(hint);
            vh.etValue.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            vh.etValue.setText(s(answers.get(key)));
            vh.etValue.addTextChangedListener(new SimpleTextWatcher(s -> {
                if (!isBinding)
                    answers.put(key, s);
            }));

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
            List<String> valueList = new ArrayList<>();

            displayList.add(tr("Select..."));
            valueList.add("");

            if (optObj instanceof List) {
                List<?> rawList = (List<?>) optObj;
                for (Object o : rawList) {
                    if (o instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<Object, Object> mo = (Map<Object, Object>) o;
                        String val = s(mo.get("value"));
                        String lab = s(mo.get("label"));
                        if (TextUtils.isEmpty(lab))
                            lab = val;
                        displayList.add(tr(lab));
                        valueList.add(val);
                    } else {
                        String s = String.valueOf(o);
                        displayList.add(tr(s));
                        valueList.add(s);
                    }
                }
            }
            final List<String> finalDisplay = displayList;
            final List<String> finalValues = valueList;

            ArrayAdapter<String> ad = new ArrayAdapter<String>(ctx, R.layout.spinner_item, finalDisplay) {
                @Override
                public boolean isEnabled(int position) {
                    return position != 0;
                }

                @Override
                public View getDropDownView(int position, View convertView, ViewGroup parent) {
                    View v = super.getDropDownView(position, convertView, parent);
                    TextView tv = (TextView) v;
                    int color = (position == 0)
                            ? ContextCompat.getColor(ctx, R.color.ss_hint)
                            : ContextCompat.getColor(ctx, R.color.ss_on_surface);
                    tv.setTextColor(color);
                    return v;
                }

                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View v = super.getView(position, convertView, parent);
                    TextView tv = v.findViewById(R.id.spinnerText);
                    if (tv == null && v instanceof TextView)
                        tv = (TextView) v;
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
            if (idxSaved < 0)
                idxSaved = 0;
            vh.spinner.setOnItemSelectedListener(null); // Prevent listener fire during setSelection
            vh.spinner.setSelection(idxSaved);
            vh.spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (isBinding)
                        return;
                    if (position == 0) {
                        answers.put(key, "");
                    } else {
                        answers.put(key, finalValues.get(position));
                    }

                    if (view instanceof TextView) {
                        ((TextView) view).setTextColor(
                                ContextCompat.getColor(ctx, position == 0 ? R.color.ss_hint : R.color.ss_on_surface));
                    } else if (view != null) {
                        TextView tv = view.findViewById(R.id.spinnerText);
                        if (tv != null) {
                            tv.setTextColor(
                                    ContextCompat.getColor(ctx,
                                            position == 0 ? R.color.ss_hint : R.color.ss_on_surface));
                        }
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });

        } else if (h instanceof VHCheckbox) {
            VHCheckbox vh = (VHCheckbox) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            vh.cb.setText(hint);
            vh.cb.setOnCheckedChangeListener(null); // Prevent fire during setChecked
            boolean checked = answers.get(key) instanceof Boolean && (Boolean) answers.get(key);
            vh.cb.setChecked(checked);
            vh.cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!isBinding)
                    answers.put(key, isChecked);
            });

        } else if (h instanceof VHSwitch) {
            VHSwitch vh = (VHSwitch) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            vh.sw.setOnCheckedChangeListener(null); // Prevent fire during setChecked
            boolean on = answers.get(key) instanceof Boolean && (Boolean) answers.get(key);
            vh.sw.setChecked(on);
            vh.sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!isBinding)
                    answers.put(key, isChecked);
            });

        } else if (h instanceof VHLocation) {
            VHLocation vh = (VHLocation) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            vh.etLocation.setHint(hint);
            vh.etLocation.setText(s(answers.get(key)));
            vh.etLocation.addTextChangedListener(new SimpleTextWatcher(s -> {
                if (!isBinding)
                    answers.put(key, s);
            }));

            vh.btnUseMyLocation.setOnClickListener(v -> {
                if (callbacks != null)
                    callbacks.requestMyLocation(key);
            });

        } else if (h instanceof VHPhotos)

        {
            VHPhotos vh = (VHPhotos) h;
            vh.tvLabel.setText(label + (req(f) ? " *" : ""));
            vh.tvHelper.setText(TextUtils.isEmpty(hint) ? "Clear, no blur" : hint);
            if (vh.tvTip != null) {
                vh.tvTip.setText(tr(
                        "Tip: The first selected photo becomes the cover. Tap any thumbnail to change or remove."));
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> ph = (Map<String, Object>) answers.get(key);
            String cover = ph == null ? "" : s(ph.get("cover")); // Base64
            @SuppressWarnings("unchecked")
            List<String> more = ph == null ? new ArrayList<>() : (List<String>) ph.get("more"); // Base64 list
            if (more == null)
                more = new ArrayList<>();
            if (vh.rv.getLayoutManager() == null) {
                vh.rv.setLayoutManager(
                        new LinearLayoutManager(vh.itemView.getContext(), RecyclerView.HORIZONTAL, false));
            }
            PhotosStripAdapter psa = new PhotosStripAdapter(
                    key,
                    cover,
                    more,
                    new PhotosStripAdapter.Events() {
                        @Override
                        public void onAddMore(String fieldKey) {
                            if (callbacks != null)
                                callbacks.pickMorePhotos(fieldKey);
                        }

                        @Override
                        public void onSetCover(String fieldKey, int indexInList) {
                            setCoverFromMore(fieldKey, indexInList);
                        }

                        @Override
                        public void onRemove(String fieldKey, int indexInMore, boolean isCover) {
                            removePhoto(fieldKey, indexInMore, isCover);
                        }
                    });

            vh.rv.setAdapter(psa);

            String msg = (TextUtils.isEmpty(cover) ? tr("Cover: not selected") : tr("Cover: selected"))
                    + "   |   " + tr("More") + ": " + more.size() + " " + tr("selected");
            vh.tvPhotoStatus.setText(msg);
        }
        isBinding = false;
    }

    @Override
    public int getItemCount() {
        int size = fields.size();
        return size;
    }

    /* ================= Photos helpers ================= */

    @SuppressLint("NotifyDataSetChanged")
    private void setCoverFromMore(String fieldKey, int indexInMore) {
        @SuppressWarnings("unchecked")
        Map<String, Object> ph = (Map<String, Object>) answers.get(fieldKey);
        if (ph == null) {
            Log.w(TAG, "setCoverFromMore: photo state missing for key=" + fieldKey);
            return;
        }

        String currentCover = normalizePhotoValue(s(ph.get("cover")));
        @SuppressWarnings("unchecked")
        List<String> more = (List<String>) ph.get("more");
        if (more == null || indexInMore < 0 || indexInMore >= more.size()) {
            Log.w(TAG, "setCoverFromMore: invalid gallery index key=" + fieldKey
                    + " index=" + indexInMore + " moreCount=" + (more == null ? 0 : more.size()));
            return;
        }

        String newCover = normalizePhotoValue(more.get(indexInMore));
        more.remove(indexInMore);

        if (!TextUtils.isEmpty(currentCover) && !samePhotoToken(currentCover, newCover)) {
            addUniquePhoto(more, currentCover);
        }

        ph.put("cover", newCover);
        ph.put("more", more);

        Log.d(TAG, "setCoverFromMore: key=" + fieldKey
                + " coverNow=" + shortToken(newCover)
                + " moreCount=" + more.size());

        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void removePhoto(String fieldKey, int indexInMore, boolean isCover) {
        @SuppressWarnings("unchecked")
        Map<String, Object> ph = (Map<String, Object>) answers.get(fieldKey);
        if (ph == null) {
            Log.w(TAG, "removePhoto: photo state missing for key=" + fieldKey);
            return;
        }

        String cover = normalizePhotoValue(s(ph.get("cover")));
        @SuppressWarnings("unchecked")
        List<String> more = (List<String>) ph.get("more");
        if (more == null) {
            more = new ArrayList<>();
            ph.put("more", more);
        }

        boolean removed = false;

        if (isCover) {
            if (!TextUtils.isEmpty(cover)) {
                Log.d(TAG, "removePhoto: removing current cover key=" + fieldKey
                        + " cover=" + shortToken(cover));
                ph.put("cover", "");
                removed = true;
            }
        } else {
            if (indexInMore >= 0 && indexInMore < more.size()) {
                String removedToken = normalizePhotoValue(more.get(indexInMore));
                more.remove(indexInMore);
                Log.d(TAG, "removePhoto: removed gallery item key=" + fieldKey
                        + " index=" + indexInMore
                        + " token=" + shortToken(removedToken)
                        + " moreLeft=" + more.size());
                removed = true;
            } else {
                Log.w(TAG, "removePhoto: invalid gallery index key=" + fieldKey
                        + " index=" + indexInMore + " moreCount=" + more.size());
            }
        }

        if (removed) {
            if (TextUtils.isEmpty(normalizePhotoValue(s(ph.get("cover")))) && !more.isEmpty()) {
                String promoted = normalizePhotoValue(more.remove(0));
                ph.put("cover", promoted);
                toast(tr("Cover removed. Promoted next image as cover."));
                Log.d(TAG, "removePhoto: promoted next image as cover key=" + fieldKey
                        + " newCover=" + shortToken(promoted)
                        + " moreLeft=" + more.size());
            } else {
                toast(tr("Photo removed."));
            }
            notifyDataSetChanged();
        }
    }


    @SuppressLint("NotifyDataSetChanged")
    public void setTextAnswer(String fieldKey, String value) {
        answers.put(fieldKey, value == null ? "" : value);
        notifyDataSetChanged();
    }

    /**
     * Batch pre-fill answers for edit mode. Handles type-aware values:
     * - Boolean for SWITCH/CHECKBOX fields
     * - String for all other fields
     * Calls notifyDataSetChanged() only once at the end.
     */
    @SuppressLint("NotifyDataSetChanged")
    public void prefillAnswers(Map<String, String> values) {
        // Build a map of key -> field type for proper type handling
        Map<String, String> fieldTypes = new HashMap<>();
        for (Map<String, Object> f : fields) {
            String key = s(f.get("key"));
            String type = s(f.get("type")).toUpperCase(Locale.ROOT);
            fieldTypes.put(key, type);
        }

        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isEmpty())
                continue;

            String fieldType = fieldTypes.get(key);
            if ("SWITCH".equals(fieldType) || "CHECKBOX".equals(fieldType)) {
                // Convert string value to Boolean
                boolean boolVal = "1".equals(value) || "true".equalsIgnoreCase(value)
                        || "yes".equalsIgnoreCase(value) || "new".equalsIgnoreCase(value);
                answers.put(key, boolVal);
            } else {
                answers.put(key, value == null ? "" : value);
            }
        }
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setCoverPhoto(String fieldKey, String base64) {
        String normalized = normalizePhotoValue(base64);
        if (TextUtils.isEmpty(normalized)) {
            Log.w(TAG, "setCoverPhoto: empty incoming cover for key=" + fieldKey);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> ph = (Map<String, Object>) answers.get(fieldKey);
        if (ph == null) {
            Log.w(TAG, "setCoverPhoto: photo state missing for key=" + fieldKey);
            return;
        }

        String oldCover = normalizePhotoValue(s(ph.get("cover")));
        @SuppressWarnings("unchecked")
        List<String> more = (List<String>) ph.get("more");
        if (more == null) {
            more = new ArrayList<>();
            ph.put("more", more);
        }

        removeMatchingPhoto(more, normalized);

        if (!TextUtils.isEmpty(oldCover) && !samePhotoToken(oldCover, normalized)) {
            addUniquePhoto(more, oldCover);
        }

        ph.put("cover", normalized);

        Log.d(TAG, "setCoverPhoto: key=" + fieldKey
                + " oldCover=" + shortToken(oldCover)
                + " newCover=" + shortToken(normalized)
                + " moreCount=" + more.size());

        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void addMorePhotos(String fieldKey, List<String> base64List) {
        if (base64List == null || base64List.isEmpty()) {
            Log.w(TAG, "addMorePhotos: empty picker result for key=" + fieldKey);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> ph = (Map<String, Object>) answers.get(fieldKey);
        if (ph == null) {
            Log.w(TAG, "addMorePhotos: photo state missing for key=" + fieldKey);
            return;
        }

        String currentCover = normalizePhotoValue(s(ph.get("cover")));

        @SuppressWarnings("unchecked")
        List<String> more = (List<String>) ph.get("more");
        if (more == null) {
            more = new ArrayList<>();
            ph.put("more", more);
        }

        List<String> normalizedNew = new ArrayList<>();
        for (String raw : base64List) {
            String normalized = normalizePhotoValue(raw);
            if (!TextUtils.isEmpty(normalized)) {
                normalizedNew.add(normalized);
            }
        }

        if (normalizedNew.isEmpty()) {
            Log.w(TAG, "addMorePhotos: all picked photos were empty after normalization for key=" + fieldKey);
            return;
        }

        boolean coverWasEmpty = TextUtils.isEmpty(currentCover);

        if (coverWasEmpty) {
            String newCover = normalizedNew.get(0);
            ph.put("cover", newCover);

            for (int i = 1; i < normalizedNew.size(); i++) {
                addUniquePhoto(more, normalizedNew.get(i));
            }

            Log.d(TAG, "addMorePhotos: key=" + fieldKey
                    + " coverWasEmpty=true"
                    + " newCover=" + shortToken(newCover)
                    + " appendedMore=" + Math.max(0, normalizedNew.size() - 1)
                    + " totalMore=" + more.size());

            notifyDataSetChanged();
            toast(tr("First photo set as cover. Tap any thumbnail to change or remove."));
            return;
        }

        int before = more.size();
        for (String normalized : normalizedNew) {
            if (!samePhotoToken(currentCover, normalized)) {
                addUniquePhoto(more, normalized);
            }
        }

        int appended = Math.max(0, more.size() - before);

        Log.d(TAG, "addMorePhotos: key=" + fieldKey
                + " coverKept=" + shortToken(currentCover)
                + " picked=" + normalizedNew.size()
                + " appended=" + appended
                + " totalMore=" + more.size());

        notifyDataSetChanged();
        toast(tr("Added ") + appended + tr(" photo(s). Tap a thumbnail to set cover or remove."));
    }


    /**
     * Pre-fill photos from existing image URLs (for edit mode).
     * First URL becomes the cover, rest go to "more" list.
     */
    @SuppressLint("NotifyDataSetChanged")
    public void prefillPhotos(String fieldKey, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            Log.w(TAG, "prefillPhotos: no incoming images for key=" + fieldKey);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> ph = (Map<String, Object>) answers.get(fieldKey);
        if (ph == null) {
            ph = new HashMap<>();
            answers.put(fieldKey, ph);
        }

        List<String> normalized = new ArrayList<>();
        for (String raw : imageUrls) {
            String token = normalizePhotoValue(raw);
            if (!TextUtils.isEmpty(token) && !containsPhotoToken(normalized, token)) {
                normalized.add(token);
            }
        }

        if (normalized.isEmpty()) {
            Log.w(TAG, "prefillPhotos: all incoming images became empty after normalization for key=" + fieldKey);
            return;
        }

        ph.put("cover", normalized.get(0));

        List<String> more = new ArrayList<>();
        for (int i = 1; i < normalized.size(); i++) {
            addUniquePhoto(more, normalized.get(i));
        }
        ph.put("more", more);

        Log.d(TAG, "prefillPhotos: key=" + fieldKey
                + " incoming=" + imageUrls.size()
                + " normalized=" + normalized.size()
                + " cover=" + shortToken(normalized.get(0))
                + " moreCount=" + more.size());

        notifyDataSetChanged();
    }


    /**
     * Get the field key for the PHOTOS type field from the schema.
     * Returns null if no PHOTOS field is found.
     */
    public String getPhotosFieldKey() {
        for (Map<String, Object> f : fields) {
            String type = s(f.get("type")).toUpperCase(Locale.ROOT);
            if ("PHOTOS".equals(type)) {
                return s(f.get("key"));
            }
        }
        return null;
    }


    private static String normalizePhotoValue(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (TextUtils.isEmpty(value)) return "";
        if (value.startsWith("data:image")) return value;
        return value;
    }

    private static boolean isRemotePhoto(String token) {
        return !TextUtils.isEmpty(token)
                && (token.startsWith("http://") || token.startsWith("https://"));
    }

    private static boolean isDataUriPhoto(String token) {
        return !TextUtils.isEmpty(token) && token.startsWith("data:image");
    }

    private static String stripDataUriPrefix(String token) {
        if (TextUtils.isEmpty(token)) return "";
        int comma = token.indexOf(',');
        if (comma >= 0 && token.startsWith("data:image")) {
            return token.substring(comma + 1);
        }
        return token;
    }

    private static boolean samePhotoToken(String a, String b) {
        return normalizePhotoValue(a).equals(normalizePhotoValue(b));
    }

    private static boolean containsPhotoToken(List<String> list, String candidate) {
        if (list == null || list.isEmpty()) return false;
        for (String item : list) {
            if (samePhotoToken(item, candidate)) return true;
        }
        return false;
    }

    private static void removeMatchingPhoto(List<String> list, String candidate) {
        if (list == null || list.isEmpty()) return;
        Iterator<String> it = list.iterator();
        while (it.hasNext()) {
            if (samePhotoToken(it.next(), candidate)) {
                it.remove();
            }
        }
    }

    private static void addUniquePhoto(List<String> list, String candidate) {
        String normalized = normalizePhotoValue(candidate);
        if (TextUtils.isEmpty(normalized)) return;
        if (!containsPhotoToken(list, normalized)) {
            list.add(normalized);
        }
    }

    private static String shortToken(String token) {
        String normalized = normalizePhotoValue(token);
        if (TextUtils.isEmpty(normalized)) return "<empty>";
        if (isRemotePhoto(normalized)) {
            int idx = normalized.lastIndexOf('/');
            return idx >= 0 ? normalized.substring(idx + 1) : normalized;
        }
        if (isDataUriPhoto(normalized)) {
            return "dataUri(len=" + normalized.length() + ")";
        }
        return "base64(len=" + normalized.length() + ")";
    }

    /* ================= Validation & JSON ================= */

    public JSONObject validateAndBuildResult() {
        try {
            for (Map<String, Object> f : fields) {
                String key = s(f.get("key"));
                String label = s(f.get("label"));
                String type = s(f.get("type"));
                boolean required = req(f);

                Object val = answers.get(key);
                String sval = val == null ? "" : String.valueOf(val);
                if (required) {
                    if ("CHECKBOX".equalsIgnoreCase(type) || "SWITCH".equalsIgnoreCase(type)) {
                        // ✅ Special case: "is_new" switch is never forced to ON.
                        // Required ka matlab yahan sirf "field exist hai", jo hamare paas hamesha hai.
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
                            // Also check if there are any URL-based photos in more list
                            @SuppressWarnings("unchecked")
                            List<String> morePhotos = ph == null ? null : (List<String>) ph.get("more");
                            boolean hasAnyPhoto = (morePhotos != null && !morePhotos.isEmpty());
                            if (!hasAnyPhoto) {
                                toast("Please add a cover photo");
                                Log.e(TAG, "Validation failed: no cover photo for " + key);
                                return null;
                            }
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
                            for (Object o : (List<?>) me.getValue())
                                arr.put(String.valueOf(o));
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
            return result;

        } catch (Exception e) {
            Log.e(TAG, "Error building JSON", e);
            toast("Error building JSON");
            return null;
        }
    }

    private void toast(String s) {
        if (callbacks != null)
            callbacks.showToast(s);
    }

    /* ================= Photos strip adapter ================= */

    static class PhotosStripAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        interface Events {
            void onAddMore(String fieldKey);

            void onSetCover(String fieldKey, int indexInList);

            void onRemove(String fieldKey, int indexInMore, boolean isCover);
        }

        private static final int V_THUMB = 1;
        private static final int V_ADD = 2;

        private final String fieldKey;
        private final String coverBase64; // base64 string (may be empty)
        private final List<String> moreBase64; // base64 strings
        private final Events events;

        PhotosStripAdapter(String fieldKey, String cover, List<String> more, Events events) {
            this.fieldKey = fieldKey;
            this.coverBase64 = cover == null ? "" : cover;
            this.moreBase64 = more == null ? new ArrayList<>() : more;
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
                    if (events != null)
                        events.onAddMore(fieldKey);
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

            try {
                bindThumbImage(vh, base64);
            } catch (Exception e) {
                Log.e(TAG, "onBindViewHolder: failed to bind thumb for key=" + fieldKey
                        + " isCover=" + isCover + " token=" + shortToken(base64), e);
                vh.iv.setImageDrawable(null);
            }

            vh.badge.setVisibility(isCover ? View.VISIBLE : View.GONE);
            if (vh.container != null) {
                vh.container.setBackgroundResource(isCover ? R.drawable.bg_thumb_cover : R.drawable.bg_thumb_normal);
            }

            if (isCover) {
                vh.name.setText(I18n.t(vh.itemView.getContext(), "Cover photo"));
            } else {
                vh.name.setText(I18n.t(vh.itemView.getContext(), "Photo") + " " + (idxInMore + 1));
            }

            vh.remove.setVisibility(View.VISIBLE);
            vh.remove.bringToFront();
            vh.remove.setOnClickListener(v -> {
                if (events != null)
                    events.onRemove(fieldKey, idxInMore, isCover);
            });

            if (!isCover) {
                vh.itemView.setOnClickListener(v -> {
                    if (events != null)
                        events.onSetCover(fieldKey, idxInMore);
                });
                vh.itemView.setOnLongClickListener(v -> {
                    if (events != null)
                        events.onSetCover(fieldKey, idxInMore);
                    return true;
                });
            } else {
                vh.itemView.setOnClickListener(null);
                vh.itemView.setOnLongClickListener(null);
            }
        }


        private void bindThumbImage(@NonNull VHT vh, String rawToken) {
            final String token = normalizePhotoValue(rawToken);
            if (TextUtils.isEmpty(token)) {
                vh.iv.setImageDrawable(null);
                vh.iv.setTag(null);
                return;
            }

            if (isRemotePhoto(token)) {
                final String imageUrl = token;
                vh.iv.setImageResource(R.drawable.ic_launcher_foreground);
                vh.iv.setTag(imageUrl);

                new Thread(() -> {
                    try {
                        java.net.URL url = new java.net.URL(imageUrl);
                        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                        conn.setDoInput(true);
                        conn.setConnectTimeout(5000);
                        conn.setReadTimeout(5000);
                        conn.connect();
                        java.io.InputStream is = conn.getInputStream();
                        Bitmap bmp = BitmapFactory.decodeStream(is);
                        is.close();

                        if (bmp != null && imageUrl.equals(vh.iv.getTag())) {
                            vh.iv.post(() -> vh.iv.setImageBitmap(bmp));
                        } else if (bmp == null) {
                            Log.w(TAG, "bindThumbImage: decoded null bitmap from URL=" + imageUrl);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "bindThumbImage: failed URL load " + imageUrl, e);
                    }
                }).start();
                return;
            }

            try {
                String cleanBase64 = stripDataUriPrefix(token).replace("\n", "").replace("\r", "");
                byte[] data = Base64.decode(cleanBase64, Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
                if (bmp != null) {
                    vh.iv.setImageBitmap(bmp);
                    vh.iv.setTag(null);
                } else {
                    Log.w(TAG, "bindThumbImage: base64 decoded but bitmap null token=" + shortToken(token));
                    vh.iv.setImageDrawable(null);
                }
            } catch (Exception e) {
                Log.e(TAG, "bindThumbImage: failed base64 decode token=" + shortToken(token), e);
                vh.iv.setImageDrawable(null);
            }
        }

        static class VHT extends RecyclerView.ViewHolder {
            View container;
            ImageView iv;
            TextView name;
            TextView badge;
            ImageView remove;

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
            VHAdd(@NonNull View v) {
                super(v);
            }
        }

        // Kept as-is (no longer used, but retained)
        private static String fileName(Context ctx, Uri uri) {
            String result = null;
            if ("content".equals(uri.getScheme())) {
                try (Cursor cursor = ctx.getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (idx >= 0)
                            result = cursor.getString(idx);
                    }
                } catch (Exception ignored) {
                }
            }
            if (result == null) {
                result = uri.getLastPathSegment();
                if (result == null)
                    result = uri.toString();
            }
            return result;
        }
    }

    /* ================= ViewHolders ================= */

    static class VHText extends RecyclerView.ViewHolder {
        TextView tvLabel;
        EditText etValue;

        VHText(@NonNull View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tvLabel);
            etValue = v.findViewById(R.id.etValue);
        }
    }

    static class VHTextArea extends RecyclerView.ViewHolder {
        TextView tvLabel;
        EditText etValue;

        VHTextArea(@NonNull View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tvLabel);
            etValue = v.findViewById(R.id.etValue);
        }
    }

    static class VHCurrencies extends RecyclerView.ViewHolder {
        TextView tvLabel;
        EditText etValue;

        VHCurrencies(@NonNull View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tvLabel);
            etValue = v.findViewById(R.id.etValue);
        }
    }

    static class VHDate extends RecyclerView.ViewHolder {
        TextView tvLabel;
        EditText etDate;

        VHDate(@NonNull View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tvLabel);
            etDate = v.findViewById(R.id.etDate);
        }
    }

    static class VHDropdown extends RecyclerView.ViewHolder {
        TextView tvLabel;
        Spinner spinner;

        VHDropdown(@NonNull View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tvLabel);
            spinner = v.findViewById(R.id.spinner);
        }
    }

    static class VHCheckbox extends RecyclerView.ViewHolder {
        TextView tvLabel;
        CheckBox cb;

        VHCheckbox(@NonNull View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tvLabel);
            cb = v.findViewById(R.id.cb);
        }
    }

    static class VHSwitch extends RecyclerView.ViewHolder {
        TextView tvLabel;
        com.google.android.material.switchmaterial.SwitchMaterial sw;

        VHSwitch(@NonNull View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tvLabel);
            sw = v.findViewById(R.id.sw);
        }
    }

    static class VHLocation extends RecyclerView.ViewHolder {
        TextView tvLabel;
        EditText etLocation;
        Button btnUseMyLocation;

        VHLocation(@NonNull View v) {
            super(v);
            tvLabel = v.findViewById(R.id.tvLabel);
            etLocation = v.findViewById(R.id.etLocation);
            btnUseMyLocation = v.findViewById(R.id.btnUseMyLocation);
        }
    }

    static class VHPhotos extends RecyclerView.ViewHolder {
        TextView tvLabel, tvHelper, tvTip, tvPhotoStatus;
        RecyclerView rv;

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
    private static String s(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

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

    public interface OnText {
        void on(String s);
    }

    static class SimpleTextWatcher implements android.text.TextWatcher {
        private final OnText cb;

        SimpleTextWatcher(OnText cb) {
            this.cb = cb;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (cb != null)
                cb.on(s.toString());
        }

        @Override
        public void afterTextChanged(android.text.Editable s) {
        }
    }
}
