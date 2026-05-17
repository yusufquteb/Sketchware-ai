package dev.aldi.sayuti.editor.manage;

import static dev.aldi.sayuti.editor.manage.LocalLibrariesUtil.createLibraryMap;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import org.cosmic.ide.dependency.resolver.api.Artifact;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.util.Helper;
import mod.jbk.build.BuiltInLibraries;
import mod.pranav.dependency.resolver.DependencyResolver;
import pro.sketchware.library.MavenCoordinateResolver;
import pro.sketchware.R;
import pro.sketchware.databinding.LibraryDownloaderDialogBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

//DR
public class LibraryDownloaderDialogFragment extends BottomSheetDialogFragment {
    private LibraryDownloaderDialogBinding binding;
    private DependencyDownloadAdapter dependencyAdapter;
    private final List<DependencyDownloadItem> downloadItems = new ArrayList<>();
    private ExecutorService downloadExecutor;

    private final Gson gson = new Gson();
    private BuildSettings buildSettings;

    private boolean notAssociatedWithProject;
    private String dependencyName;
    /** Set when opening from Update dialog: old library folder name to delete after download. */
    private String oldLibraryName;
    /** Set when opening from Update dialog: old dependency coordinate. */
    private String oldDependencyCoord;
    private String localLibFile;
    private OnLibraryDownloadedTask onLibraryDownloadedTask;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = LibraryDownloaderDialogBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (downloadExecutor != null && !downloadExecutor.isShutdown()) {
            downloadExecutor.shutdownNow();
        }
        unregisterNetworkCallback();
        binding = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() == null) return;

        dependencyAdapter = new DependencyDownloadAdapter();
        binding.dependenciesRecyclerView.setAdapter(dependencyAdapter);
        binding.dependenciesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        downloadExecutor = Executors.newSingleThreadExecutor();

        notAssociatedWithProject = getArguments().getBoolean("notAssociatedWithProject", false);
        buildSettings = (BuildSettings) getArguments().getSerializable("buildSettings");
        localLibFile = getArguments().getString("localLibFile");

        // Pre-fill dependency field when opened from the Update dialog
        String prefill = getArguments().getString("prefillDependency", null);
        if (prefill != null && !prefill.isEmpty()) {
            binding.dependencyInput.setText(prefill);
        }
        oldLibraryName     = getArguments().getString("oldLibraryName", null);
        oldDependencyCoord = getArguments().getString("oldDependencyCoord", null);

        binding.btnDownload.setOnClickListener(v -> initDownloadFlow());

        connectivityManager = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        registerNetworkCallback();
    }

    private void registerNetworkCallback() {
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                if (binding != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (binding != null) binding.btnDownload.setEnabled(true);
                    });
                }
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                if (binding != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (binding != null) binding.btnDownload.setEnabled(false);
                    });
                }
            }
        };
        connectivityManager.registerDefaultNetworkCallback(networkCallback);
        // Initial check
        binding.btnDownload.setEnabled(isNetworkAvailable());
    }

    private void unregisterNetworkCallback() {
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }

        String[] options = new String[]{"21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32", "33", "34"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                options
        );
        binding.actChooseminapi.setAdapter(adapter);
    }

    public void setOnLibraryDownloadedTask(OnLibraryDownloadedTask onLibraryDownloadedTask) {
        this.onLibraryDownloadedTask = onLibraryDownloadedTask;
    }

    private void initDownloadFlow() {
        dependencyName = Helper.getText(binding.dependencyInput);
        if (dependencyName.isEmpty()) {
            binding.dependencyInputLayout.setError("Please enter a dependency");
            binding.dependencyInputLayout.setErrorEnabled(true);
            return;
        }

        var parts = dependencyName.split(":");
        if (parts.length != 3) {
            binding.dependencyInputLayout.setError("Invalid dependency format");
            binding.dependencyInputLayout.setErrorEnabled(true);
            return;
        }

        showDownloadConfirmationDialog(parts[0], parts[1], parts[2]);
    }

    private void showDownloadConfirmationDialog(String group, String artifact, String version) {
        boolean skipSubdependencies = binding.cbSkipSubdependencies.isChecked();

        String message;
        if (skipSubdependencies) {
            message = "Are you sure you want to download " + dependencyName;
        } else {
            message = "Are you sure you want to download " + dependencyName + " and its sub-dependencies?";
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Confirm Download")
                .setMessage(message)
                .setPositiveButton("Download", (dialog, which) -> startDownloadProcess(group, artifact, version))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startDownloadProcess(String group, String artifact, String version) {
        binding.dependencyInputLayout.setErrorEnabled(false);

        binding.dependencyInfo.setVisibility(View.GONE);
        binding.overallProgress.setVisibility(View.VISIBLE);
        binding.dependenciesRecyclerView.setVisibility(View.VISIBLE);

        setDownloadState(true);

        var resolver = new DependencyResolver(group, artifact, version,
                binding.cbSkipSubdependencies.isChecked(), buildSettings);
        var handler = new Handler(Looper.getMainLooper());

        downloadExecutor.execute(() -> {
            BuiltInLibraries.maybeExtractAndroidJar((message, progress) ->
                    handler.post(() -> binding.overallProgress.setIndeterminate(true)));
            BuiltInLibraries.maybeExtractCoreLambdaStubsJar();

            resolver.resolveDependency(new DependencyResolver.DependencyResolverCallback() {
                @Override
                public void onResolving(@NonNull Artifact artifact, @NonNull Artifact dependency) {
                    handler.post(() -> {
                        DependencyDownloadItem item = findOrCreateDependencyItem(dependency);
                        item.setState(DependencyDownloadItem.DownloadState.RESOLVING);
                        dependencyAdapter.updateDependency(item);
                    });
                }

                @Override
                public void onResolutionComplete(@NonNull Artifact dep) {
                    handler.post(() -> updateDependencyState(dep, DependencyDownloadItem.DownloadState.COMPLETED));
                }

                @Override
                public void onArtifactNotFound(@NonNull Artifact dep) {
                    handler.post(() -> {
                        setDownloadState(false);
                        SketchwareUtil.showAnErrorOccurredDialog(getActivity(), "Dependency '" + dep + "' not found");
                    });
                }

                @Override
                public void onSkippingResolution(@NonNull Artifact dep) {
                    handler.post(() -> {
                        DependencyDownloadItem item = findOrCreateDependencyItem(dep);
                        item.setState(DependencyDownloadItem.DownloadState.COMPLETED);
                        dependencyAdapter.updateDependency(item);
                    });
                }

                @Override
                public void onVersionNotFound(@NonNull Artifact dep) {
                    handler.post(() -> {
                        DependencyDownloadItem item = findOrCreateDependencyItem(dep);
                        item.setError("Version not available");
                        dependencyAdapter.updateDependency(item);
                    });
                }

                @Override
                public void onDependenciesNotFound(@NonNull Artifact dep) {
                    handler.post(() -> {
                        DependencyDownloadItem item = findOrCreateDependencyItem(dep);
                        item.setError("Dependencies not found");
                        dependencyAdapter.updateDependency(item);
                    });
                }

                @Override
                public void onInvalidScope(@NonNull Artifact dep, @NonNull String scope) {
                    handler.post(() -> {
                        DependencyDownloadItem item = findOrCreateDependencyItem(dep);
                        item.setError("Invalid scope: " + scope);
                        dependencyAdapter.updateDependency(item);
                    });
                }

                @Override
                public void invalidPackaging(@NonNull Artifact dep) {
                    handler.post(() -> {
                        DependencyDownloadItem item = findOrCreateDependencyItem(dep);
                        item.setError("Invalid packaging");
                        dependencyAdapter.updateDependency(item);
                    });
                }

                @Override
                public void onDownloadStart(@NonNull Artifact dep) {
                    handler.post(() -> {
                        setDownloadState(true);
                        DependencyDownloadItem item = findOrCreateDependencyItem(dep);
                        item.setState(DependencyDownloadItem.DownloadState.DOWNLOADING);
                        dependencyAdapter.updateDependency(item);
                        updateOverallProgress();
                    });
                }

                @Override
                public void onDownloadEnd(@NonNull Artifact dep) {
                    handler.post(() -> {
                        updateDependencyState(dep, DependencyDownloadItem.DownloadState.COMPLETED);
                        updateOverallProgress();
                    });
                }

                @Override
                public void onDownloadError(@NonNull Artifact dep, @NonNull Throwable e) {
                    handler.post(() -> {
                        DependencyDownloadItem item = findOrCreateDependencyItem(dep);
                        item.setError(e.getMessage());
                        dependencyAdapter.updateDependency(item);
                        setDownloadState(false);
                        SketchwareUtil.showAnErrorOccurredDialog(getActivity(),
                                "Downloading dependency '" + dep + "' failed: " + Log.getStackTraceString(e));
                    });
                }

                @Override
                public void unzipping(@NonNull Artifact artifact) {
                    handler.post(() -> updateDependencyState(artifact, DependencyDownloadItem.DownloadState.UNZIPPING));
                }

                @Override
                public void dexing(@NonNull Artifact dep) {
                    handler.post(() -> updateDependencyState(dep, DependencyDownloadItem.DownloadState.DEXING));
                }

                @Override
                public void dexingFailed(@NonNull Artifact dependency, @NonNull Exception e) {
                    handler.post(() -> {
                        DependencyDownloadItem item = findOrCreateDependencyItem(dependency);
                        item.setError("Dexing failed: " + e.getMessage());
                        dependencyAdapter.updateDependency(item);
                        setDownloadState(false);
                        SketchwareUtil.showAnErrorOccurredDialog(getActivity(),
                                "Dexing dependency '" + dependency + "' failed: " + Log.getStackTraceString(e));
                    });
                }

                @Override
                public void onTaskCompleted(@NonNull List<String> dependencies) {
                    handler.post(() -> {
                        SketchwareUtil.toast("Library downloaded successfully");

                        String localLibsRoot = pro.sketchware.utility.FileUtil.getExternalStorageDir()
                                + "/.sketchware/libs/local_libs/";

                        // Save dependency coordinate (ALWAYS overwrite – handles updates)
                        for (String libName : dependencies) {
                            String coord = deriveDependencyForLib(libName, dependencyName);
                            if (coord != null) {
                                FileUtil.writeFile(localLibsRoot + libName + "/dependency", coord);
                            }
                        }

                        // If this was a real update (old library name ≠ primary artifact folder),
                        // delete the old library folder and update all project references.
                        if (oldLibraryName != null && !oldLibraryName.isEmpty()) {
                            // Primary downloaded folder = first in dependencies list
                            String newLibName = dependencies.isEmpty() ? null : dependencies.get(0);
                            if (newLibName != null && !newLibName.equals(oldLibraryName)) {
                                // Delete old library folder
                                pro.sketchware.utility.FileUtil.deleteFile(
                                    localLibsRoot + oldLibraryName);
                                // Update all project local_library files
                                String newCoord = deriveDependencyForLib(newLibName, dependencyName);
                                ManageLocalLibraryActivity.updateLibraryInAllProjects(
                                    oldLibraryName, newLibName, oldDependencyCoord, newCoord);
                            } else if (newLibName != null) {
                                // Same folder name – coord change only
                                String newCoord = deriveDependencyForLib(newLibName, dependencyName);
                                ManageLocalLibraryActivity.updateLibraryInAllProjects(
                                    oldLibraryName, oldLibraryName, oldDependencyCoord, newCoord);
                            }
                        }

                        if (!notAssociatedWithProject) {
                            var fileContent = FileUtil.readFile(localLibFile);
                            var enabledLibs = gson.fromJson(fileContent, Helper.TYPE_MAP_LIST);
                            enabledLibs.addAll(dependencies.stream()
                                    .map(name -> createLibraryMap(name, dependencyName))
                                    .toList());
                            FileUtil.writeFile(localLibFile, gson.toJson(enabledLibs));
                        }
                        if (getActivity() == null) return;
                        dismiss();
                        if (onLibraryDownloadedTask != null) onLibraryDownloadedTask.invoke();
                    });
                }
            });
        });
    }

    /**
     * Resolves Maven coordinate for a downloaded library folder.
     * Priority: primary dep string > lookup table > best-effort extraction.
     */
    private String deriveDependencyForLib(String libName, String primaryDependency) {
        // 1. If this is the primary artifact itself
        String[] primaryParts = primaryDependency.split(":");
        if (primaryParts.length >= 2) {
            if (libName.toLowerCase().contains(primaryParts[1].toLowerCase())) {
                return primaryDependency;
            }
        }
        // 2. Static lookup table (80+ common libs, instant, no network)
        String resolved = pro.sketchware.library.LibraryVersionChecker.resolveCoordinate(libName);
        if (resolved != null) return resolved;

        // 3. Best-effort: primary group + extracted artifact + version
        String version = pro.sketchware.library.LibraryVersionChecker.extractVersionFromName(libName);
        if (version != null && primaryParts.length >= 1) {
            String artBase = libName.replaceAll("[_-][Vv]?" + java.util.regex.Pattern.quote(version) + "$", "")
                                    .replaceAll("[_-]$", "");
            return primaryParts[0] + ":" + artBase + ":" + version;
        }
        return null;
    }

    private DependencyDownloadItem findOrCreateDependencyItem(Artifact artifact) {
        for (DependencyDownloadItem item : downloadItems) {
            if (item.getArtifact().equals(artifact)) {
                return item;
            }
        }
        DependencyDownloadItem newItem = new DependencyDownloadItem(artifact);
        downloadItems.add(newItem);
        dependencyAdapter.addDependency(newItem);
        return newItem;
    }

    private void updateDependencyState(Artifact artifact, DependencyDownloadItem.DownloadState state) {
        for (DependencyDownloadItem item : downloadItems) {
            if (item.getArtifact().equals(artifact)) {
                item.setState(state);
                dependencyAdapter.updateDependency(item);
                break;
            }
        }
    }

    private void updateOverallProgress() {
        if (binding == null) return;
        int completed = 0;
        for (DependencyDownloadItem item : downloadItems) {
            if (item.isCompleted()) completed++;
        }

        if (!downloadItems.isEmpty()) {
            binding.overallProgress.setIndeterminate(false);
            binding.overallProgress.setProgress((completed * 100) / downloadItems.size());
        }
    }

    private void setDownloadState(boolean downloading) {
        // Guard: fragment may have been detached/destroyed while download was in progress
        if (binding == null) return;
        if (downloading) {
            binding.btnDownload.setVisibility(View.GONE);
        } else {
            binding.btnDownload.setVisibility(View.VISIBLE);
            binding.btnDownload.setEnabled(true);
        }

        binding.dependencyInput.setEnabled(!downloading);
        binding.cbSkipSubdependencies.setEnabled(!downloading);
        setCancelable(!downloading);

        if (!downloading) {
            binding.dependencyInfo.setVisibility(View.VISIBLE);
            binding.overallProgress.setVisibility(View.GONE);
            binding.dependenciesRecyclerView.setVisibility(View.GONE);
            binding.dependencyInfo.setText(R.string.local_library_manager_dependency_info);

            downloadItems.clear();
            dependencyAdapter.setDependencies(new ArrayList<>());
        }
    }

    private boolean isNetworkAvailable() {
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
        return capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    public interface OnLibraryDownloadedTask {
        void invoke();
    }
}