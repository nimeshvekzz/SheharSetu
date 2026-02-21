package com.infowave.sheharsetu.net;

public final class ApiRoutes {
    // अपने सर्वर का बेस URL भरो (trailing slash के बिना)
    public static final String BASE_URL = "https://magenta-owl-444153.hostingersite.com/api";

    public static final String SEND_OTP = BASE_URL + "/send_otp.php";
    public static final String VERIFY_OTP = BASE_URL + "/verify_otp.php";

    public static final String SAVE_PROFILE = BASE_URL + "/save_profile.php";

    public static final String GET_STATES = BASE_URL + "/list_states.php";
    public static final String GET_DISTRICTS = BASE_URL + "/list_districts.php";

    public static final String GET_LANGUAGES = BASE_URL + "/list_languages.php";

    public static final String GET_CATEGORIES = BASE_URL + "/list_categories.php";
    public static final String GET_SUBCATEGORIES = BASE_URL + "/list_subcategories.php";
    public static final String GET_PRODUCTS = BASE_URL + "/list_products.php";

    public static final String GET_LISTING = BASE_URL + "/get_listing.php";
    public static final String GET_LISTING_DETAILS = BASE_URL + "/get_listing_details.php";

    public static final String GET_FORM_SCHEMA = BASE_URL + "/get_form_schema.php";

    // User Profile
    public static final String GET_USER_PROFILE = BASE_URL + "/get_user_profile.php";
    public static final String UPDATE_USER_PROFILE = BASE_URL + "/update_user_profile.php";

    public static final String CREATE_LISTING = BASE_URL + "/create_listing.php";
    public static final String UPDATE_LISTING = BASE_URL + "/update_listing.php";

    // My Listings
    public static final String GET_USER_LISTINGS = BASE_URL + "/get_user_listings.php";
    public static final String MARK_LISTING_SOLD = BASE_URL + "/mark_listing_sold.php";
    public static final String DELETE_LISTING = BASE_URL + "/delete_listing.php";
    public static final String REPOST_LISTING = BASE_URL + "/repost_listing.php";

    private ApiRoutes() {
    }
}
