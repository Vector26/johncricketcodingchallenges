import requests
import logging
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed

# Configure logging to provide timestamp, log level, and message
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

# Function to send a single request to the given URL
def send_request(url):
    try:
        response = requests.get(url)
        if response.status_code == 200:
            logging.info(f"Response from server: {response.text}")
        return response.status_code
    except requests.exceptions.RequestException as e:
        # Log any request errors and return None to indicate failure
        logging.error(f"Request failed: {e}")
        return None

# Main function that drives the load testing
def main(port, number_of_requests):
    # Construct the URL using the provided port
    url = f"http://localhost:{port}"
    logging.info(f"Starting load test with {number_of_requests} simultaneous requests to {url}")

    # Variables to track the number of successful and failed requests
    success_count = 0
    failure_count = 0

    # Maximum number of threads is 30
    max_workers = min(50, int(number_of_requests * 0.25))

    # Create a ThreadPoolExecutor to manage concurrent requests
    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        # Submit all requests to the executor and store the future objects
        future_to_request = {executor.submit(send_request, url): i for i in range(number_of_requests)}
        # Iterate over completed requests and update success/failure counts
        for future in as_completed(future_to_request):
            result = future.result()
            if result and result == 200:
                success_count += 1
            else:
                failure_count += 1

    # Log the total counts of successful and failed requests
    logging.info(f"Load test completed. Success: {success_count}, Failure: {failure_count}")

if __name__ == "__main__":
    # Set up argument parsing for command-line flexibility
    parser = argparse.ArgumentParser(description='Load Balancer Test Script')
    parser.add_argument('--port', type=int, default=1003, help='Port where the load balancer is running')
    parser.add_argument('--requests', type=int, default=5000, help='Number of simultaneous requests')
    args = parser.parse_args()

    # Run the main function with the provided arguments
    main(args.port, args.requests)
