-- consolidated initial schema for pico possum
-- combines all previous migrations (v1-v5) into a single baseline

pragma foreign_keys=on;

-- 1. users & auth
create table users (
    id integer primary key autoincrement,
    username text not null unique,
    password_hash text not null,
    name text,
    email text,
    is_active integer default 1,
    last_login datetime,
    created_at datetime default current_timestamp,
    updated_at datetime default current_timestamp,
    deleted_at datetime
);

create table sessions (
    id integer primary key autoincrement,
    user_id integer not null,
    token text not null unique,
    expires_at datetime not null,
    created_at datetime default current_timestamp,
    foreign key (user_id) references users(id) on delete cascade
);

-- 2. inventory & catalog
create table categories (
    id integer primary key autoincrement,
    name text not null unique,
    parent_id integer,
    created_at datetime default current_timestamp,
    updated_at datetime default current_timestamp,
    deleted_at datetime,
    foreign key (parent_id) references categories(id)
);

create table products (
    id integer primary key autoincrement,
    name text not null,
    description text,
    category_id integer,
    status text check(status in ('active','inactive','discontinued')) default 'active',
    image_path text,
    sku text,
    mrp numeric(10,2) default 0,
    cost_price numeric(10,2) default 0,
    stock_alert_cap integer default 10,
    created_at datetime default current_timestamp,
    updated_at datetime default current_timestamp,
    deleted_at datetime,
    foreign key (category_id) references categories(id)
);

create table inventory_lots (
    id integer primary key autoincrement,
    product_id integer not null,
    batch_number text,
    manufactured_date text,
    expiry_date text,
    quantity integer not null default 0,
    unit_cost numeric not null default 0,
    created_at text not null default current_timestamp,
    foreign key (product_id) references products(id) on delete cascade
);

create table inventory_adjustments (
    id integer primary key autoincrement,
    product_id integer not null,
    lot_id integer,
    quantity_change integer not null,
    reason text check(reason in ('sale','return','confirm_receive','spoilage','damage','theft','correction','product_deleted')) not null,
    reference_type text,
    reference_id integer,
    adjusted_by integer not null,
    notes text,
    adjusted_at datetime default current_timestamp,
    foreign key (product_id) references products(id) on delete cascade,
    foreign key (lot_id) references inventory_lots(id) on delete set null,
    foreign key (adjusted_by) references users(id)
);

create table product_stock_cache (
    product_id integer primary key,
    current_stock integer not null default 0,
    last_updated datetime default current_timestamp,
    foreign key (product_id) references products(id) on delete cascade
);

-- 3. customers
create table customers (
    id integer primary key autoincrement,
    name text not null,
    phone text,
    email text,
    address text,
    customer_type text,
    loyalty_points integer default 0,
    created_at datetime default current_timestamp,
    updated_at datetime default current_timestamp,
    deleted_at datetime
);

-- 4. sales & commercial
create table sales (
    id integer primary key autoincrement,
    invoice_id text unique,
    invoice_number text not null unique,
    sale_date datetime default current_timestamp,
    total_amount numeric(10,2) not null,
    paid_amount numeric(10,2) not null,
    discount numeric(10,2) default 0,
    status text check(status in ('draft','paid','partially_paid','cancelled','refunded','partially_refunded')) not null,
    fulfillment_status text check(fulfillment_status in ('pending','fulfilled','cancelled')) not null default 'pending',
    customer_id integer,
    user_id integer not null,
    payment_method_id integer,
    foreign key (customer_id) references customers(id),
    foreign key (user_id) references users(id),
    foreign key (payment_method_id) references payment_methods(id)
);

create table sale_items (
    id integer primary key autoincrement,
    sale_id integer not null,
    product_id integer not null,
    quantity integer not null,
    price_per_unit numeric(10,2) not null,
    cost_per_unit numeric(10,2) not null,
    discount_amount numeric(10,2) default 0,
    foreign key (sale_id) references sales(id) on delete cascade,
    foreign key (product_id) references products(id)
);

create table legacy_sales (
    id integer primary key autoincrement,
    invoice_number text not null unique,
    sale_date datetime not null,
    customer_code text,
    customer_name text,
    net_amount numeric(10,2) not null check(net_amount >= 0),
    source_file text,
    payment_method_id integer,
    payment_method_name text,
    imported_at datetime default current_timestamp,
    updated_at datetime default current_timestamp
);

create table returns (
    id integer primary key autoincrement,
    invoice_id text unique,
    sale_id integer not null,
    user_id integer not null,
    reason text,
    refund_amount numeric(10,2) default 0,
    payment_method_id integer,
    created_at datetime default current_timestamp,
    foreign key (sale_id) references sales(id),
    foreign key (user_id) references users(id),
    foreign key (payment_method_id) references payment_methods(id)
);

create table return_items (
    id integer primary key autoincrement,
    return_id integer not null,
    sale_item_id integer not null,
    quantity integer not null check (quantity > 0),
    refund_amount numeric(10,2) not null check (refund_amount >= 0),
    foreign key (return_id) references returns(id) on delete cascade,
    foreign key (sale_item_id) references sale_items(id) on delete cascade
);

-- 5. payments
create table payment_methods (
    id integer primary key autoincrement,
    name text not null unique,
    code text,
    is_active integer not null default 1 check(is_active in (0,1))
);

-- 6. analytics & reports
create table product_flow (
    id integer primary key autoincrement,
    product_id integer not null,
    event_type text check(event_type in ('sale','return','adjustment')) not null,
    quantity integer not null,
    reference_type text,
    reference_id integer,
    event_date datetime default current_timestamp,
    foreign key (product_id) references products(id) on delete cascade
);

create table sales_reports (
    id integer primary key autoincrement,
    report_type text check(report_type in ('daily','monthly','yearly')) not null,
    period_start date not null,
    period_end date not null,
    total_sales numeric(10,2) not null,
    total_discount numeric(10,2) not null,
    total_transactions integer not null,
    generated_at datetime default current_timestamp
);

-- 7. audit & ui state
create table audit_log (
    id integer primary key autoincrement,
    user_id integer not null,
    action text not null,
    table_name text,
    row_id integer,
    old_data text,
    new_data text,
    event_details text,
    ip_address text,
    user_agent text,
    severity text default 'info',
    created_at datetime default current_timestamp,
    foreign key (user_id) references users(id)
);

create table query_performance_log (
    id integer primary key autoincrement,
    query_name text not null,
    execution_time_ms integer not null,
    row_count integer,
    executed_at datetime default current_timestamp
);

create table drafts (
    id text primary key,
    type text not null,
    payload text not null,
    user_id integer not null,
    updated_at text not null default current_timestamp,
    foreign key(user_id) references users(id) on delete cascade
);

create table pos_open_bills (
    bill_index integer primary key,
    customer_id integer,
    customer_name text,
    customer_phone text,
    customer_email text,
    customer_address text,
    payment_method_id integer,
    overall_discount real,
    is_discount_fixed integer,
    amount_tendered real,
    updated_at text default current_timestamp
);

create table invoice_sequences (
    payment_type_code text primary key,
    last_sequence integer not null
);

create table pos_open_bill_items (
    id integer primary key autoincrement,
    bill_index integer,
    product_id integer,
    quantity integer,
    price_per_unit real,
    discount_value real,
    discount_type text,
    foreign key(bill_index) references pos_open_bills(bill_index) on delete cascade,
    foreign key(product_id) references products(id)
);

-- 8. indexes
create index idx_users_username on users(username);
create index idx_sessions_token on sessions(token);
create index idx_sessions_user_id on sessions(user_id);
create index idx_customers_name on customers(name);
create index idx_customers_phone on customers(phone);
create index idx_customers_deleted on customers(deleted_at);
create index idx_categories_parent on categories(parent_id);
create index idx_products_category on products(category_id);
create index idx_products_sku on products(sku);
create index idx_products_deleted on products(deleted_at);
create index idx_inventory_lots_product on inventory_lots(product_id);
create index idx_inventory_adjustments_product on inventory_adjustments(product_id);
create index idx_product_stock_cache_stock on product_stock_cache(current_stock);
create index idx_sales_invoice on sales(invoice_number);
create index idx_sales_invoice_id on sales(invoice_id);
create index idx_sales_date on sales(sale_date);
create index idx_sales_customer on sales(customer_id);
create index idx_sale_items_sale on sale_items(sale_id);
create index idx_sale_items_product on sale_items(product_id);
create index idx_returns_invoice_id on returns(invoice_id);
create index idx_legacy_sales_invoice on legacy_sales(invoice_number);
create index idx_legacy_sales_date on legacy_sales(sale_date);
create index idx_product_flow_product on product_flow(product_id);
create index idx_product_flow_date on product_flow(event_date);
create index idx_audit_log_user on audit_log(user_id);
create index idx_audit_log_date on audit_log(created_at);
create index idx_drafts_type_user on drafts(type, user_id);

-- 9. seed data
insert into payment_methods (id, name, code, is_active) values 
(1, 'Cash', 'CH', 1),
(2, 'Card', 'CP', 1),
(3, 'UPI', 'UP', 1),
(4, 'Gift Card', 'GC', 1);

-- 10. triggers
create trigger customers_updated_at_trig after update on customers begin
  update customers set updated_at = current_timestamp where id = old.id;
end;

create trigger trg_update_stock_cache_lot_insert after insert on inventory_lots begin
  insert into product_stock_cache (product_id, current_stock, last_updated)
  values (new.product_id, new.quantity, current_timestamp)
  on conflict(product_id) do update set
    current_stock = current_stock + new.quantity,
    last_updated = current_timestamp;
end;

create trigger trg_update_stock_cache_adjustment after insert on inventory_adjustments 
when new.reason != 'confirm_receive'
begin
  insert into product_stock_cache (product_id, current_stock, last_updated)
  values (new.product_id, new.quantity_change, current_timestamp)
  on conflict(product_id) do update set
    current_stock = current_stock + new.quantity_change,
    last_updated = current_timestamp;
end;
