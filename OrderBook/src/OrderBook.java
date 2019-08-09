
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Scanner;
import java.io.*;


public class OrderBook {
	enum Type{
		Market,
		Limit,
		Stop,
		Cancel;
	}
	
	enum Side {
		Buy,
		Sell,
		NA;
	}
	
	enum HasHoldPrice {
		True,
		False;
	}
	
	static class Order {
		Type type;
		Side side;
		double price;
		int timestamp;
		int quantity;
		int id;
		
		
		Order() {
			
		}
		
		Order(int id, String type, String side, double price, int quantity){
			this.id = id;
			if (type.equals("Market")) {
				this.type = Type.Market;
			} else if (type.equals("Limit")) {
				this.type = Type.Limit;
			} else if (type.equals("Stop")) {
				this.type = Type.Stop;
			} else {
				this.type = Type.Cancel;
			}
			
			if (side.equals("Buy")) {
				this.side = Side.Buy;				
			} else if (side.equals("Sell")){
				this.side = Side.Sell;
			} else {
				this.side = Side.NA;
			}
			
			this.price = price;
			this.quantity = quantity;
		}			
	}
	
	private static Map<Integer, Order> ordersListMap = new HashMap<>();
	
	private Map<Double, List<Order>> bidMap = new HashMap<>();
	private Map<Double, List<Order>> offerMap = new HashMap<>();
	
	PriorityQueue<Order> buy_heap = new PriorityQueue<Order>(new Comparator<Order>() {
		@Override
		public int compare(Order o1, Order o2) {
			if (o1.price == o2.price) {
				return o1.timestamp < o2.timestamp ? -1 : 1;
			}
			return o1.price > o2.price ? -1 : 1;
		}
	});

	PriorityQueue<Order> sell_heap = new PriorityQueue<Order>(new Comparator<Order>() {
		@Override
		public int compare(Order o1, Order o2) {
			if (o1.price == o2.price) {
				return o1.timestamp < o2.timestamp ? -1 : 1;
			}
			return o1.price < o2.price ? -1 : 1;
		}
	});
	
	private Order cur_order = null;
	private double max_trade_price = Double.MIN_VALUE;
	private double min_trade_price = Double.MAX_VALUE;
	boolean trade_finished = false;
	
	public void createNewOrder(Order order) {
		Order newBid = new Order();
		List<Order> bucket = getBucket(bidMap, newBid.price);		
		bucket.add(newBid);
		bidMap.put(newBid.price, bucket);
		buy_heap.offer(newBid);
		ordersListMap.put(newBid.id, newBid);
	}

	private List<Order> getBucket(Map<Double, List<Order>> map, double price) {
		//
		List<Order> bucket;
		if (map.containsKey(price)) {
			bucket = map.get(price);
		} else {
			bucket = new LinkedList<Order>();
		}
		return bucket;
	}
	//create sell order
	public void addOffer(Order order) {
		Order newOffer = new Order();
		List<Order> bucket = getBucket(offerMap, newOffer.price);
		bucket.add(newOffer);
		offerMap.put(newOffer.price, bucket);
		sell_heap.offer(newOffer);
		ordersListMap.put(newOffer.id, newOffer);
	}
	
	public void matchBid (Order order, HasHoldPrice hasPrice) {
		while(!sell_heap.isEmpty() && order.quantity > 0) {
			Order match_order = sell_heap.peek();
			//match_order价格必须小于预期价格才能交易，否则就break
			if (hasPrice == HasHoldPrice.True && order.price < match_order.price) {
				break;
			}
			
			match(order, match_order);
			//heap里面这个价格的order已经没有了
			if (match_order.quantity == 0) {
				removeOrderFromOrdersListMap(match_order.id);
				sell_heap.poll();
			}
		}
	}
	
	public void matchAsk(Order order, HasHoldPrice hasPrice) {
		while (!buy_heap.isEmpty() && order.quantity > 0) {
			Order match_order = buy_heap.peek();
			//如果有价格门槛 && 目前order价格比buy-heap里的大
			//match_order的价格比预期价格大才卖
			if (hasPrice == HasHoldPrice.True && order.price > match_order.price) {
				break;
			}
			match(order, match_order);
			if (match_order.quantity == 0) {
				removeOrderFromOrdersListMap(match_order.id);
				buy_heap.poll();
			}
		}
	}

	private void removeOrderFromOrdersListMap(int id) {

		
		ordersListMap.remove(id);
	
	}

	private void match(Order order, Order match_order) {
		// TODO Auto-generated method stub
		int trade_volumn = Math.min(order.quantity, match_order.quantity);
		order.quantity = order.quantity - trade_volumn;
		match_order.quantity = match_order.quantity - trade_volumn;
		max_trade_price = Math.max(max_trade_price, match_order.price);
		min_trade_price = Math.min(min_trade_price, match_order.price);
		trade_finished = true;
	}
	
	
	public void checkStopOrder() {
		boolean trigger = true;
		while(trade_finished && trigger) {
			boolean executed = false;
			for (Order o : ordersListMap.values()) {
				switch(o.side) {
				case Buy :
					//20的时候没有超低成功，22的时候赶紧买，超过就不买了
					if (o.price <= max_trade_price && trade_finished) {
						executed = true;
						matchBid(o, HasHoldPrice.False);
					}
					break;
				case Sell :
					if (o.price >= min_trade_price && trade_finished) {
						executed = true;
						matchAsk(o, HasHoldPrice.False);
					}
					break;
				default:
					break;
				}
				if (executed) {
					removeOrderFromOrdersListMap(o.id);
				}
			}
			trigger = executed;
		}
		max_trade_price = Double.MIN_VALUE;
		min_trade_price = Double.MAX_VALUE;
		trade_finished = false;
	}
	
	public void execute() {
		switch(cur_order.type) {
		case Market:
			switch(cur_order.side) {
			case Buy :
				matchBid(cur_order, HasHoldPrice.False);
				break;
			case Sell:
				matchAsk(cur_order, HasHoldPrice.False);
				break;
			}
			break;
		
		case Limit :
			switch(cur_order.side) {
			case Buy :
				matchBid(cur_order, HasHoldPrice.True);
				break;
			case Sell:
				matchAsk(cur_order, HasHoldPrice.True);
				break;
			}
			//
			if (cur_order.quantity != 0) {
				switch(cur_order.side) {
				case Buy :
					buy_heap.offer(cur_order);
					createNewOrder(cur_order);
					break;
				case Sell:
					sell_heap.offer(cur_order);
					addOffer(cur_order);
					break;
				default:
					break;
				}
			}
			break;
			
		case Stop:
			break;
			
		case Cancel :
			//order要存在且没交易
			if (ordersListMap.get(cur_order.id) != null) {
				Order cancel_order = ordersListMap.get(cur_order.id);
				switch (cancel_order.type) {
					case Limit:
						switch(cancel_order.side) {
							case Buy :
								buy_heap.remove(cancel_order);
								break;
							case Sell:
								sell_heap.remove(cancel_order);
						}
						removeOrderFromOrdersListMap(cancel_order.id);
						break;
					
					case Stop :
						removeOrderFromOrdersListMap(cancel_order.id);
						break;					
				}
			}
			break;					
		}
	}

	public static void main(String[] args) throws FileNotFoundException {
		// TODO Auto-generated method stub
		//int id;
		int count = 1;
		File file = new File("/Users/huangechen/Desktop/StockOrder.txt");
		Scanner sc = new Scanner(file);		
		while(sc.hasNext()) {
			String input = sc.nextLine();
			String[] array = input.split(",");
			int id = Integer.parseInt(array[0]);
			String type = array[1];
			System.out.println(type);
			String side = array[2];
			System.out.println(side);
			double price = Double.parseDouble(array[3]);
			int quantity = Integer.parseInt(array[4]);
			
			Order order = new Order(count++, type, side, price, quantity);
			ordersListMap.put(order.id, order);
			OrderBook orderBook = new OrderBook();
			orderBook.createNewOrder(order);
			//orderBook.execute();
			orderBook.checkStopOrder();			
		}
	}
}
