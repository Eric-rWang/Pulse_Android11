package tan.philip.nrf_ble;

import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.View;
import androidx.databinding.DataBinderMapper;
import androidx.databinding.DataBindingComponent;
import androidx.databinding.ViewDataBinding;
import java.lang.IllegalArgumentException;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.RuntimeException;
import java.lang.String;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import tan.philip.nrf_ble.databinding.ActivityGraphBindingImpl;
import tan.philip.nrf_ble.databinding.ActivityScanningBindingImpl;

public class DataBinderMapperImpl extends DataBinderMapper {
  private static final int LAYOUT_ACTIVITYGRAPH = 1;

  private static final int LAYOUT_ACTIVITYSCANNING = 2;

  private static final SparseIntArray INTERNAL_LAYOUT_ID_LOOKUP = new SparseIntArray(2);

  static {
    INTERNAL_LAYOUT_ID_LOOKUP.put(tan.philip.nrf_ble.R.layout.activity_graph, LAYOUT_ACTIVITYGRAPH);
    INTERNAL_LAYOUT_ID_LOOKUP.put(tan.philip.nrf_ble.R.layout.activity_scanning, LAYOUT_ACTIVITYSCANNING);
  }

  @Override
  public ViewDataBinding getDataBinder(DataBindingComponent component, View view, int layoutId) {
    int localizedLayoutId = INTERNAL_LAYOUT_ID_LOOKUP.get(layoutId);
    if(localizedLayoutId > 0) {
      final Object tag = view.getTag();
      if(tag == null) {
        throw new RuntimeException("view must have a tag");
      }
      switch(localizedLayoutId) {
        case  LAYOUT_ACTIVITYGRAPH: {
          if ("layout/activity_graph_0".equals(tag)) {
            return new ActivityGraphBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_graph is invalid. Received: " + tag);
        }
        case  LAYOUT_ACTIVITYSCANNING: {
          if ("layout/activity_scanning_0".equals(tag)) {
            return new ActivityScanningBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_scanning is invalid. Received: " + tag);
        }
      }
    }
    return null;
  }

  @Override
  public ViewDataBinding getDataBinder(DataBindingComponent component, View[] views, int layoutId) {
    if(views == null || views.length == 0) {
      return null;
    }
    int localizedLayoutId = INTERNAL_LAYOUT_ID_LOOKUP.get(layoutId);
    if(localizedLayoutId > 0) {
      final Object tag = views[0].getTag();
      if(tag == null) {
        throw new RuntimeException("view must have a tag");
      }
      switch(localizedLayoutId) {
      }
    }
    return null;
  }

  @Override
  public int getLayoutId(String tag) {
    if (tag == null) {
      return 0;
    }
    Integer tmpVal = InnerLayoutIdLookup.sKeys.get(tag);
    return tmpVal == null ? 0 : tmpVal;
  }

  @Override
  public String convertBrIdToString(int localId) {
    String tmpVal = InnerBrLookup.sKeys.get(localId);
    return tmpVal;
  }

  @Override
  public List<DataBinderMapper> collectDependencies() {
    ArrayList<DataBinderMapper> result = new ArrayList<DataBinderMapper>(1);
    result.add(new androidx.databinding.library.baseAdapters.DataBinderMapperImpl());
    return result;
  }

  private static class InnerBrLookup {
    static final SparseArray<String> sKeys = new SparseArray<String>(1);

    static {
      sKeys.put(0, "_all");
    }
  }

  private static class InnerLayoutIdLookup {
    static final HashMap<String, Integer> sKeys = new HashMap<String, Integer>(2);

    static {
      sKeys.put("layout/activity_graph_0", tan.philip.nrf_ble.R.layout.activity_graph);
      sKeys.put("layout/activity_scanning_0", tan.philip.nrf_ble.R.layout.activity_scanning);
    }
  }
}
