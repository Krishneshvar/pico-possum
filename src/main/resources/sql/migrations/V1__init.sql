-- Pico Possum: Minimalist Production Schema

pragma foreign_keys=on;

-- 1. Identity
create table users (
    id integer primary key autoincrement,
    username text not null unique,
    password_hash text not null,
    name text,
    is_active integer default 1 check(is_active in (0,1)),
    created_at datetime default current_timestamp,
    updated_at datetime default current_timestamp,
    deleted_at datetime
);

-- 2. Catalog & Products
create table categories (
    id integer primary key autoincrement,
    name text not null unique,
    parent_id integer,
    created_at datetime default current_timestamp,
    updated_at datetime default current_timestamp,
    deleted_at datetime,
    foreign key (parent_id) references categories(id) on delete set null
);

create table products (
    id integer primary key autoincrement,
    name text not null,
    description text,
    category_id integer,
    status text check(status in ('active','inactive','discontinued')) default 'active',
    image_path text,
    sku text unique,
    mrp numeric(10,2) not null default 0,
    cost_price numeric(10,2) not null default 0,
    stock_alert_cap integer default 10,
    created_at datetime default current_timestamp,
    updated_at datetime default current_timestamp,
    deleted_at datetime,
    foreign key (category_id) references categories(id) on delete set null
);

-- 3. Inventory & Movements
create table stock_movements (
    id integer primary key autoincrement,
    product_id integer not null,
    quantity_change integer not null,
    reason text check(reason in ('sale','return','receive','damage','theft','correction','cleanup')) not null,
    reference_type text, 
    reference_id integer,
    notes text,
    created_at datetime default current_timestamp,
    foreign key (product_id) references products(id) on delete cascade
);

create table product_stock_cache (
    product_id integer primary key,
    current_stock integer not null default 0,
    last_updated datetime default current_timestamp,
    foreign key (product_id) references products(id) on delete cascade
);

-- 4. CRM
create table customers (
    id integer primary key autoincrement,
    name text not null,
    phone text,
    email text,
    address text,
    customer_type text,
    created_at datetime default current_timestamp,
    updated_at datetime default current_timestamp,
    deleted_at datetime
);

-- 5. Commercial Flow
create table payment_methods (
    id integer primary key autoincrement,
    name text not null unique,
    code text unique,
    is_active integer default 1 check(is_active in (0,1))
);

create table sales (
    id integer primary key autoincrement,
    invoice_number text not null unique,
    invoice_id text,
    sale_date datetime default current_timestamp,
    total_amount numeric(10,2) not null,
    paid_amount numeric(10,2) not null,
    discount numeric(10,2) default 0,
    status text check(status in ('draft','paid','partially_paid','cancelled','refunded','partially_refunded')) not null,
    fulfillment_status text default 'fulfilled',
    customer_id integer,
    payment_method_id integer,
    foreign key (customer_id) references customers(id) on delete set null,
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
    foreign key (product_id) references products(id) on delete restrict
);

create table returns (
    id integer primary key autoincrement,
    invoice_id text unique,
    sale_id integer not null,
    reason text,
    refund_amount numeric(10,2) default 0,
    payment_method_id integer,
    created_at datetime default current_timestamp,
    foreign key (sale_id) references sales(id) on delete cascade,
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

-- 6. Insights & Reports
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

-- 7. Legacy Data Support
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

-- 8. System Management & UI State
create table audit_log (
    id integer primary key autoincrement,
    action text not null,
    table_name text,
    row_id integer,
    old_data text,
    new_data text,
    event_details text,
    severity text default 'info',
    created_at datetime default current_timestamp
);

create table drafts (
    id text primary key,
    type text not null,
    payload text not null,
    updated_at datetime default current_timestamp
);

create table pos_open_bills (
    bill_index integer primary key,
    customer_id integer,
    customer_name text,
    customer_phone text,
    customer_email text,
    customer_address text,
    payment_method_id integer,
    overall_discount numeric(10,2) default 0,
    is_discount_fixed integer default 1,
    amount_tendered numeric(10,2) default 0,
    updated_at datetime default current_timestamp,
    foreign key(customer_id) references customers(id) on delete set null
);

create table pos_open_bill_items (
    id integer primary key autoincrement,
    bill_index integer not null,
    product_id integer not null,
    quantity integer not null,
    price_per_unit numeric(10,2) not null,
    discount_value numeric(10,2) default 0,
    discount_type text default 'percentage',
    foreign key(bill_index) references pos_open_bills(bill_index) on delete cascade,
    foreign key(product_id) references products(id) on delete cascade
);

create table invoice_sequences (
    payment_type_code text primary key,
    last_sequence integer not null
);

-- 9. Production Optimized Indexes
create index idx_users_name on users(username);
create index idx_customers_search on customers(name, phone);
create index idx_products_sku_act on products(sku) where deleted_at is null;
create index idx_stock_movements_prod on stock_movements(product_id, created_at);
create index idx_sales_ledger on sales(invoice_number, sale_date);
create index idx_sale_items_ref on sale_items(sale_id, product_id);
create index idx_returns_ledger on returns(invoice_id, sale_id);
create index idx_product_flow_stats on product_flow(product_id, event_date);
create index idx_sales_reports_period on sales_reports(period_start, period_end);
create index idx_legacy_sales_inv on legacy_sales(invoice_number);
create index idx_audit_timeline on audit_log(created_at);

-- 10. Seed Data
insert into payment_methods (id, name, code) values 
(1, 'Cash', 'CH'),
(2, 'Card', 'CP'),
(3, 'UPI', 'UP'),
(4, 'Gift Card', 'GC');

-- 11. Business Integrity Triggers
-- Automated stock cache update
create trigger trg_stock_auto_cache after insert on stock_movements begin
  insert into product_stock_cache (product_id, current_stock, last_updated)
  values (new.product_id, new.quantity_change, current_timestamp)
  on conflict(product_id) do update set
    current_stock = current_stock + new.quantity_change,
    last_updated = current_timestamp;
end;

-- Automated Insight generation (populates product_flow from stock_movements)
create trigger trg_insights_sync after insert on stock_movements begin
  insert into product_flow (product_id, event_type, quantity, reference_type, reference_id, event_date)
  values (
    new.product_id, 
    case when new.reason = 'sale' then 'sale' when new.reason = 'return' then 'return' else 'adjustment' end,
    new.quantity_change,
    new.reference_type,
    new.reference_id,
    new.created_at
  );
end;

create trigger trg_products_updated_at after update on products begin
  update products set updated_at = current_timestamp where id = old.id;
end;
