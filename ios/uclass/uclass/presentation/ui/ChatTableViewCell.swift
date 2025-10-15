import Combine
import SwiftUI
import UIKit

// MARK: - UITableViewCell
class ChatTableViewCell: UITableViewCell {
    private let bubbleView = UIView()
    private let messageLabel = UILabel()
    private let timeLabel = UILabel()
    private let stackView = UIStackView()
    private let messageStackView = UIStackView()
    
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
        
        // 메인 스택뷰 설정
        stackView.axis = .horizontal
        stackView.distribution = .fill
        stackView.alignment = .bottom
        stackView.spacing = 4
        
        // 메시지 스택뷰 설정 (버블 + 시간)
        messageStackView.axis = .horizontal
        messageStackView.distribution = .fill
        messageStackView.alignment = .bottom
        messageStackView.spacing = 4
        
        // 버블뷰 설정
        bubbleView.layer.cornerRadius = 16
        bubbleView.layer.masksToBounds = true
        
        // 메시지 라벨 설정
        messageLabel.numberOfLines = 0
        messageLabel.font = UIFont.systemFont(ofSize: 16)
        messageLabel.textAlignment = .left
        
        // 시간 라벨 설정
        timeLabel.font = UIFont.systemFont(ofSize: 11)
        timeLabel.textColor = UIColor(red: 0.6, green: 0.6, blue: 0.6, alpha: 1.0)
        timeLabel.setContentHuggingPriority(.required, for: .horizontal)
        timeLabel.setContentCompressionResistancePriority(.required, for: .horizontal)
        
        // 계층 구조 설정
        bubbleView.addSubview(messageLabel)
        contentView.addSubview(stackView)
        
        // Auto Layout 설정
        stackView.translatesAutoresizingMaskIntoConstraints = false
        messageLabel.translatesAutoresizingMaskIntoConstraints = false
        timeLabel.translatesAutoresizingMaskIntoConstraints = false
        
        NSLayoutConstraint.activate([
            stackView.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 4),
            stackView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -4),
            stackView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16),
            stackView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16),
            
            messageLabel.topAnchor.constraint(equalTo: bubbleView.topAnchor, constant: 12),
            messageLabel.bottomAnchor.constraint(equalTo: bubbleView.bottomAnchor, constant: -12),
            messageLabel.leadingAnchor.constraint(equalTo: bubbleView.leadingAnchor, constant: 16),
            messageLabel.trailingAnchor.constraint(equalTo: bubbleView.trailingAnchor, constant: -16),
            
            bubbleView.widthAnchor.constraint(lessThanOrEqualToConstant: 280)
        ])
    }
    
    func configure(with message: ChatMessage) {
        messageLabel.text = message.text
        timeLabel.text = message.timeString
        
        // 스택뷰 초기화
        stackView.arrangedSubviews.forEach { $0.removeFromSuperview() }
        messageStackView.arrangedSubviews.forEach { $0.removeFromSuperview() }
        
        if message.isMe {
            // 내 메시지 (오른쪽 정렬, 시간은 왼쪽)
            bubbleView.backgroundColor = UIColor(red: 0.00, green: 0.13, blue: 0.93, alpha: 1.0)
            messageLabel.textColor = .white
            
            // 시간 + 버블 순서
            messageStackView.addArrangedSubview(timeLabel)
            messageStackView.addArrangedSubview(bubbleView)
            
            let spacer = UIView()
            stackView.addArrangedSubview(spacer)
            stackView.addArrangedSubview(messageStackView)
        } else {
            // 상대방 메시지 (왼쪽 정렬, 시간은 오른쪽)
            bubbleView.backgroundColor = UIColor(red: 0.94, green: 0.94, blue: 0.94, alpha: 1.0)
            messageLabel.textColor = .black
            
            // 버블 + 시간 순서
            messageStackView.addArrangedSubview(bubbleView)
            messageStackView.addArrangedSubview(timeLabel)
            
            stackView.addArrangedSubview(messageStackView)
            let spacer = UIView()
            stackView.addArrangedSubview(spacer)
        }
    }
}
