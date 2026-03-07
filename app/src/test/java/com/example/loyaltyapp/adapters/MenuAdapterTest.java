package com.example.loyaltyapp.ui;

import android.os.Build;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.example.loyaltyapp.R;
import com.example.loyaltyapp.models.MenuItemModel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for MenuAdapter to ensure list mapping and click interactions
 * correctly handle MenuItemModel representations.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = { Build.VERSION_CODES.O_MR1 })
public class MenuAdapterTest {

    private MenuAdapter adapter;
    private List<MenuItemModel> menuList;
    private RecyclerView recyclerView;
    private boolean clickTriggered;

    @Before
    public void setUp() {
        menuList = new ArrayList<>();
        clickTriggered = false;

        recyclerView = new RecyclerView(ApplicationProvider.getApplicationContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(ApplicationProvider.getApplicationContext()));
    }

    /**
     * Test list sizing updates successfully on submit.
     */
    @Test
    public void testGetItemCount() {
        adapter = new MenuAdapter(menuList, null);
        assertEquals(0, adapter.getItemCount());

        MenuItemModel model = new MenuItemModel();
        menuList.add(model);
        adapter.submit(menuList);
        assertEquals(1, adapter.getItemCount());
    }

    /**
     * Test that view binding works correctly mapping MenuItemModel attributes.
     */
    @Test
    public void testOnBindViewHolderFormatting() {
        MenuItemModel model = new MenuItemModel();
        model.setId("menu_1");
        model.setName("Espresso");
        model.setPriceMAD(20.0);

        menuList.add(model);
        adapter = new MenuAdapter(menuList, item -> clickTriggered = true);
        recyclerView.setAdapter(adapter);

        MenuAdapter.VH viewHolder = adapter.onCreateViewHolder(recyclerView, 0);
        adapter.onBindViewHolder(viewHolder, 0);

        TextView titleTextView = viewHolder.itemView.findViewById(R.id.itemTitle);
        TextView priceTextView = viewHolder.itemView.findViewById(R.id.itemPrice);

        assertEquals("Espresso", titleTextView.getText().toString());
        assertEquals("20 MAD", priceTextView.getText().toString());

        // Test click listener
        viewHolder.itemView.performClick();
        assertTrue("Item click listener should be triggered", clickTriggered);
    }
}
