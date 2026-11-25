package com.wyrix.wallpaper.ui

import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import com.wyrix.wallpaper.BuildConfig
import com.wyrix.wallpaper.ads.UnityAdsManager
import com.wyrix.wallpaper.R
import com.wyrix.wallpaper.data.FavoritesStore
import com.wyrix.wallpaper.databinding.ActivityMainBinding
import com.wyrix.wallpaper.model.WallpaperItem
import com.wyrix.wallpaper.network.Category
import com.wyrix.wallpaper.network.WallpaperRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var wallpaperAdapter: WallpaperAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private var bannerView: BannerView? = null
    private val gson = Gson()

    private val loadedItems = mutableListOf<WallpaperItem>()
    private var currentCategory: Category = WallpaperRepository.categories.first()
    private var currentQuery: String? = null
    private var currentPage = 1
    private var isLoading = false
    private var hasMore = true

    // Incremented on every reload so a slow, old request can never touch the new list.
    private var generation = 0
    private var loadJob: Job? = null

    private val isFavoritesScreen: Boolean
        get() = currentQuery == null && currentCategory.id == WallpaperRepository.FAVORITES_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCategoryList()
        setupWallpaperGrid()
        setupSearch()
        setupCredits()
        setupBannerAd()

        binding.swipeRefresh.setOnRefreshListener { reload() }

        loadNextPage()
    }

    /**
     * Unity banner (320x50) at the bottom of the main screen. Debug builds run in Unity test
     * mode (see UnityAdsManager). The container stays empty if the ad fails to load.
     */
    private fun setupBannerAd() {
        if (!UnityAdsManager.isEnabled) return
        val banner = BannerView(this, BuildConfig.UNITY_BANNER_ID, UnityBannerSize(320, 50))
        banner.listener = object : BannerView.IListener {
            override fun onBannerLoaded(bannerAdView: BannerView?) {
                binding.adContainer.removeAllViews()
                binding.adContainer.addView(banner)
            }

            override fun onBannerShown(bannerAdView: BannerView?) {}
            override fun onBannerClick(bannerAdView: BannerView?) {}
            override fun onBannerFailedToLoad(bannerAdView: BannerView?, errorInfo: BannerErrorInfo?) {}
            override fun onBannerLeftApplication(bannerAdView: BannerView?) {}
        }
        bannerView = banner
        loadBannerWhenReady(banner, 0)
    }

    /** The Unity SDK initialises asynchronously, so retry a few times until it is ready. */
    private fun loadBannerWhenReady(banner: BannerView, attempt: Int) {
        if (UnityAdsManager.isInitialized) {
            banner.load()
        } else if (attempt < 10) {
            binding.adContainer.postDelayed({ loadBannerWhenReady(banner, attempt + 1) }, 1000)
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        // The user may have removed a favorite on the detail screen.
        if (isFavoritesScreen && !isLoading) reload()
    }

    override fun onDestroy() {
        bannerView?.destroy()
        super.onDestroy()
    }

    // ---------------- Setup ----------------

    private fun setupCategoryList() {
        binding.categoryRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        categoryAdapter = CategoryAdapter(WallpaperRepository.categories) { category ->
            currentCategory = category
            currentQuery = null
            binding.searchView.setQuery("", false)
            binding.searchView.clearFocus()
            reload()
        }
        binding.categoryRecycler.adapter = categoryAdapter
    }

    private fun setupWallpaperGrid() {
        wallpaperAdapter = WallpaperAdapter { item -> openDetail(item) }
        val layoutManager = GridLayoutManager(this, 2)
        binding.wallpaperRecycler.layoutManager = layoutManager
        binding.wallpaperRecycler.adapter = wallpaperAdapter

        binding.wallpaperRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || isLoading || !hasMore) return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (lastVisible >= layoutManager.itemCount - 8) {
                    loadNextPage()
                }
            }
        })
    }

    private fun setupSearch() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val q = query?.trim()
                if (!q.isNullOrEmpty()) {
                    currentQuery = q
                    categoryAdapter.clearSelection()
                    binding.searchView.clearFocus()
                    reload()
                }
                return true
            }

            override fun onQueryTextChange(newText: String?) = false
        })
    }

    private fun setupCredits() {
        binding.creditsText.setText(
            if (WallpaperRepository.hasPexels) R.string.credit_footer_pexels else R.string.credit_footer
        )
        binding.creditsText.setOnClickListener { showCredits() }
    }

    private fun showCredits() {
        val html = buildString {
            append(getString(R.string.credit_pixabay))
            append("<br><br>")
            if (WallpaperRepository.hasPexels) {
                append(getString(R.string.credit_pexels))
                append("<br><br>")
            }
            append(getString(R.string.credit_nasa))
            append("<br><br>")
            append(getString(R.string.credit_sdo))
            append("<br><br>")
            append(getString(R.string.credit_esa))
            append("<br><br>")
            append(getString(R.string.credit_eso))
            append("<br><br>")
            append(getString(R.string.credit_note))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.credits_title)
            .setMessage(HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT))
            .setPositiveButton(android.R.string.ok, null)
            .show()
        dialog.findViewById<TextView>(android.R.id.message)?.movementMethod =
            LinkMovementMethod.getInstance()
    }

    private fun openDetail(item: WallpaperItem) {
        val intent = Intent(this, WallpaperDetailActivity::class.java)
        intent.putExtra(WallpaperDetailActivity.EXTRA_ITEM_JSON, gson.toJson(item))
        intent.putExtra(
            WallpaperDetailActivity.EXTRA_AMOLED_DEFAULT,
            currentQuery == null && currentCategory.amoledByDefault
        )
        startActivity(intent)
    }

    // ---------------- Loading ----------------

    private fun reload() {
        generation++
        loadJob?.cancel()
        isLoading = false
        hasMore = true
        currentPage = 1
        loadedItems.clear()
        wallpaperAdapter.submitList(emptyList())
        binding.emptyText.visibility = View.GONE
        loadNextPage()
    }

    private fun loadNextPage() {
        if (isLoading || !hasMore) return
        isLoading = true
        val gen = generation
        binding.progressBar.visibility = View.VISIBLE

        loadJob = lifecycleScope.launch {
            var succeeded = false
            try {
                val results: List<WallpaperItem> = when {
                    isFavoritesScreen -> {
                        hasMore = false
                        FavoritesStore.getAll(this@MainActivity)
                    }
                    currentQuery != null ->
                        WallpaperRepository.load(WallpaperRepository.searchFeeds(currentQuery!!), currentPage)
                    else ->
                        WallpaperRepository.load(currentCategory.feeds, currentPage)
                }
                if (gen != generation) return@launch

                if (results.isEmpty()) hasMore = false
                val known = loadedItems.map { it.id }.toHashSet()
                loadedItems.addAll(results.filter { known.add(it.id) })
                wallpaperAdapter.submitList(loadedItems.toList())
                currentPage++
                succeeded = true

                binding.emptyText.setText(
                    if (isFavoritesScreen) R.string.no_favorites else R.string.no_results
                )
                binding.emptyText.visibility =
                    if (loadedItems.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == generation && loadedItems.isEmpty()) {
                    binding.emptyText.setText(R.string.no_results)
                    binding.emptyText.visibility = View.VISIBLE
                }
            } finally {
                if (gen == generation) {
                    isLoading = false
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                }
            }

            // If the first page was too short to scroll, keep loading until the screen is full.
            if (gen == generation && succeeded && hasMore && loadedItems.size < 12) {
                loadNextPage()
            }
        }
    }
}
