import UIKit

class DateSeparatorCell: UITableViewCell {
    private let containerView = UIView()
    private let dateLabel = UILabel()
    
    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        setupUI()
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    private func setupUI() {
        backgroundColor = .clear
        selectionStyle = .none
        
        // 컨테이너 뷰 설정
        containerView.backgroundColor = UIColor(red: 0.9, green: 0.9, blue: 0.9, alpha: 1.0)
        containerView.layer.cornerRadius = 12
        
        // 날짜 라벨 설정
        dateLabel.font = UIFont.systemFont(ofSize: 12, weight: .medium)
        dateLabel.textColor = UIColor(red: 0.4, green: 0.4, blue: 0.4, alpha: 1.0)
        dateLabel.textAlignment = .center
        
        // 계층 구조 설정
        containerView.addSubview(dateLabel)
        contentView.addSubview(containerView)
        
        // Auto Layout 설정
        containerView.translatesAutoresizingMaskIntoConstraints = false
        dateLabel.translatesAutoresizingMaskIntoConstraints = false
        
        NSLayoutConstraint.activate([
            containerView.centerXAnchor.constraint(equalTo: contentView.centerXAnchor),
            containerView.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 16),
            containerView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -8),
            
            dateLabel.topAnchor.constraint(equalTo: containerView.topAnchor, constant: 6),
            dateLabel.bottomAnchor.constraint(equalTo: containerView.bottomAnchor, constant: -6),
            dateLabel.leadingAnchor.constraint(equalTo: containerView.leadingAnchor, constant: 12),
            dateLabel.trailingAnchor.constraint(equalTo: containerView.trailingAnchor, constant: -12)
        ])
    }
    
    func configure(with date: Date) {
        dateLabel.text = ChatMessage.dateFormatter.string(from: date)
    }
}
